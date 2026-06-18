package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.api.dto.request.PaymentConfirmRequest;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import com.pickbit.paymentservice.exception.InvalidPaymentStatusException;
import com.pickbit.paymentservice.exception.PaymentAccessDeniedException;
import com.pickbit.paymentservice.exception.PaymentAmountMismatchException;
import com.pickbit.paymentservice.exception.PaymentNotFoundException;
import com.pickbit.paymentservice.exception.PgUnavailableException;
import com.pickbit.paymentservice.infrastructure.client.TossPaymentsClient;
import com.pickbit.paymentservice.infrastructure.client.dto.TossPaymentResponse;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import com.pickbit.paymentservice.infrastructure.persistence.SettlementRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock PaymentRepository paymentRepository;
    @Mock TossPaymentsClient tossPaymentsClient;
    @Mock OutboxRecorder outboxRecorder;
    @Mock TransactionTemplate transactionTemplate;
    @Mock SettlementRepository settlementRepository;

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
        lenient().doAnswer(invocation -> {
            invocation.getArgument(0, java.util.function.Consumer.class).accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
        ReflectionTestUtils.setField(paymentCommandService, "confirmTimeoutDays", 7);
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
    @DisplayName("이미 ESCROWED 인 결제를 confirm 하면 현재 결제 정보를 반환하고 토스를 호출하지 않는다")
    void confirm_alreadyEscrowedReturnsCurrentPayment() {
        payment.markPgPending(PAYMENT_KEY);
        payment.markEscrowed(PAYMENT_KEY, LocalDateTime.now(), 7);
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));

        var response = paymentCommandService.confirm(BUYER_ID, new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT));

        assertThat(response.status()).isEqualTo(PaymentStatus.ESCROWED);
        verifyNoInteractions(tossPaymentsClient, outboxRecorder);
    }

    @Test
    @DisplayName("PG_PENDING 결제를 confirm 하면 승인 처리 중 예외를 반환하고 토스를 호출하지 않는다")
    void confirm_pgPendingThrowsInProgress() {
        payment.markPgPending(PAYMENT_KEY);
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.confirm(BUYER_ID,
                new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
                .isInstanceOf(InvalidPaymentStatusException.class)
                .hasMessageContaining("결제 승인 처리 중");
        verifyNoInteractions(tossPaymentsClient, outboxRecorder);
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
    @DisplayName("토스 confirm 실패 시 PG_PENDING 결제를 FAILED 로 전환한다")
    void confirm_tossFailureMarksPgFailed() {
        PgUnavailableException failure = new PgUnavailableException("PG 장애");
        given(paymentRepository.findByPgOrderIdForUpdate(ORDER_ID)).willReturn(Optional.of(payment));
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));
        given(tossPaymentsClient.confirm(PAYMENT_KEY, ORDER_ID, AMOUNT)).willThrow(failure);

        assertThatThrownBy(() -> paymentCommandService.confirm(BUYER_ID,
                new PaymentConfirmRequest(PAYMENT_KEY, ORDER_ID, AMOUNT)))
                .isSameAs(failure);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.getPgPaymentKey()).isEqualTo(PAYMENT_KEY);
        verifyNoInteractions(outboxRecorder);
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

    @Test
    @DisplayName("구매확정 성공: ESCROWED → RELEASED, 정산 생성, SETTLED outbox 발행")
    void confirmPurchase_success() {
        payment.markPgPending(PAYMENT_KEY);
        payment.markEscrowed(PAYMENT_KEY, LocalDateTime.now(), 7);
        Settlement settlement = newSettlement();
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));
        given(settlementRepository.findByPaymentId(payment.getId())).willReturn(Optional.empty());
        given(settlementRepository.save(any(Settlement.class))).willReturn(settlement);

        paymentCommandService.confirmPurchase(BUYER_ID, payment.getId());

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RELEASED);
        assertThat(payment.getReleasedAt()).isNotNull();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getSettledAt()).isNotNull();
        verify(outboxRecorder).paymentSettledEvent(payment, settlement);
    }

    @Test
    @DisplayName("이미 RELEASED 인 결제 구매확정은 성공 응답하고 outbox 를 재발행하지 않는다")
    void confirmPurchase_alreadyReleased() {
        payment.markPgPending(PAYMENT_KEY);
        payment.markEscrowed(PAYMENT_KEY, LocalDateTime.now(), 7);
        payment.markReleased(LocalDateTime.now());
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));

        paymentCommandService.confirmPurchase(BUYER_ID, payment.getId());

        verifyNoInteractions(settlementRepository, outboxRecorder);
    }

    @Test
    @DisplayName("구매자가 아닌 사용자가 구매확정하면 PaymentAccessDeniedException")
    void confirmPurchase_accessDenied() {
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.confirmPurchase(999L, payment.getId()))
                .isInstanceOf(PaymentAccessDeniedException.class);
        verifyNoInteractions(settlementRepository, outboxRecorder);
    }

    @Test
    @DisplayName("ESCROWED 가 아닌 결제를 구매확정하면 InvalidPaymentStatusException")
    void confirmPurchase_invalidStatus() {
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentCommandService.confirmPurchase(BUYER_ID, payment.getId()))
                .isInstanceOf(InvalidPaymentStatusException.class);
        verifyNoInteractions(settlementRepository, outboxRecorder);
    }

    private Settlement newSettlement() {
        Settlement settlement = Settlement.builder()
                .paymentId(payment.getId())
                .grossAmount(AMOUNT)
                .platformFeeAmount(BigDecimal.ZERO)
                .pgFeeAmount(BigDecimal.ZERO)
                .netSellerAmount(AMOUNT)
                .status(SettlementStatus.PENDING)
                .build();
        ReflectionTestUtils.setField(settlement, "id", 1L);
        return settlement;
    }
}
