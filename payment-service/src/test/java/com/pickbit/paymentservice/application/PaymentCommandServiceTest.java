package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.api.dto.request.PaymentConfirmRequest;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import com.pickbit.paymentservice.exception.InvalidPaymentStatusException;
import com.pickbit.paymentservice.exception.PaymentAccessDeniedException;
import com.pickbit.paymentservice.exception.PaymentAmountMismatchException;
import com.pickbit.paymentservice.exception.PaymentNotFoundException;
import com.pickbit.paymentservice.infrastructure.client.TossPaymentsClient;
import com.pickbit.paymentservice.infrastructure.client.dto.TossPaymentResponse;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock TossPaymentsClient tossPaymentsClient;
    @Mock OutboxRecorder outboxRecorder;
    @Mock TransactionTemplate transactionTemplate;

    @InjectMocks PaymentCommandService paymentCommandService;

    private Payment payment;
    private static final Long BUYER_ID = 100L;
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(50_000);
    private static final String ORDER_ID = "order-uuid";
    private static final String PAYMENT_KEY = "toss-pk";

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class).doInTransaction(null));
        ReflectionTestUtils.setField(paymentCommandService, "confirmTimeoutDays", 10);
        payment = Payment.builder()
                .auctionId(1L)
                .buyerUserId(BUYER_ID)
                .sellerUserId(200L)
                .amount(AMOUNT)
                .pgProvider(PgProvider.TOSS_PAYMENTS)
                .status(PaymentStatus.REQUESTED)
                .paymentDeadlineAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPgOrderId(ORDER_ID);
    }

    @Test
    @DisplayName("정상 confirm: REQUESTED → ESCROWED 로 전이되고 outbox 1건 발행")
    void confirm_success() {
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));
        given(tossPaymentsClient.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT))
                .willReturn(new TossPaymentResponse(PAYMENT_KEY, ORDER_ID, "DONE", AMOUNT, AMOUNT, "CARD", null));

        paymentCommandService.confirm(BUYER_ID, new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.ESCROWED);
        assertThat(payment.getPgPaymentKey()).isEqualTo(PAYMENT_KEY);
        assertThat(payment.getPaidAt()).isNotNull();
        verify(outboxRecorder).paymentEscrowedEvent(payment);
    }

    @Test
    @DisplayName("orderId 로 결제를 찾을 수 없으면 PaymentNotFoundException")
    void confirm_notFound() {
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentCommandService.confirm(BUYER_ID,
                new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
                .isInstanceOf(PaymentNotFoundException.class);
        verifyNoInteractions(tossPaymentsClient, outboxRecorder);
    }

    @Test
    @DisplayName("다른 사용자가 confirm 시도하면 PaymentAccessDeniedException")
    void confirm_accessDenied() {
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.confirm(999L,
                new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
                .isInstanceOf(PaymentAccessDeniedException.class);
        verifyNoInteractions(tossPaymentsClient, outboxRecorder);
    }

    @Test
    @DisplayName("amount 가 다르면 PaymentAmountMismatchException — 토스 호출 안 함")
    void confirm_amountMismatch() {
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.confirm(BUYER_ID,
                new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, BigDecimal.valueOf(40_000))))
                .isInstanceOf(PaymentAmountMismatchException.class);
        verifyNoInteractions(tossPaymentsClient, outboxRecorder);
    }

    @Test
    @DisplayName("이미 ESCROWED 인 결제를 confirm 하면 InvalidPaymentStatusException")
    void confirm_invalidStatus() {
        payment.markPgPending(PAYMENT_KEY);
        payment.markEscrowed(PAYMENT_KEY, LocalDateTime.now(), 10);
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.confirm(BUYER_ID,
                new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
                .isInstanceOf(InvalidPaymentStatusException.class);
    }

    @Test
    @DisplayName("결제 마감 시간이 지나면 InvalidPaymentStatusException")
    void confirm_deadlinePassed() {
        ReflectionTestUtils.setField(payment, "paymentDeadlineAt", LocalDateTime.now().minusMinutes(1));
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.confirm(BUYER_ID,
                new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
                .isInstanceOf(InvalidPaymentStatusException.class);
    }

    @Test
    @DisplayName("정상 refund: ESCROWED → REFUNDED + outbox 발행")
    void refund_success() {
        payment.markPgPending(PAYMENT_KEY);
        payment.markEscrowed(PAYMENT_KEY, LocalDateTime.now(), 10);
        given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));
        given(tossPaymentsClient.cancel(eq(PAYMENT_KEY), any(), eq(AMOUNT)))
                .willReturn(new TossPaymentResponse(PAYMENT_KEY, ORDER_ID, "CANCELED", BigDecimal.ZERO, AMOUNT, "CARD", null));

        paymentCommandService.refund(BUYER_ID, payment.getId(), "변심");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.getRefundedAt()).isNotNull();
        verify(outboxRecorder).paymentRefundedEvent(payment, "변심");
    }

    @Test
    @DisplayName("ESCROWED 가 아닌 결제 refund 시도하면 InvalidPaymentStatusException")
    void refund_invalidStatus() {
        given(paymentRepository.findById(payment.getId())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.refund(BUYER_ID, payment.getId(), "변심"))
                .isInstanceOf(InvalidPaymentStatusException.class);
        verifyNoInteractions(tossPaymentsClient, outboxRecorder);
    }
}
