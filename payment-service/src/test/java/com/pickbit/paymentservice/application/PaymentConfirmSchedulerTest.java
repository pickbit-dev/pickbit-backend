package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmSchedulerTest {

    @Mock PaymentRepository paymentRepository;
    @Mock PaymentCommandService paymentCommandService;

    @InjectMocks PaymentConfirmScheduler scheduler;

    @Test
    @DisplayName("구매확정 기한이 지난 ESCROWED 결제를 자동 구매확정한다")
    void autoConfirmPurchases() {
        Payment payment = newEscrowedPayment();
        given(paymentRepository.findByStatusAndConfirmDeadlineAtBefore(eq(PaymentStatus.ESCROWED), any()))
                .willReturn(List.of(payment));
        given(paymentCommandService.autoConfirmPurchase(eq(payment.getId()), any())).willReturn(true);

        scheduler.autoConfirmPurchases();

        verify(paymentCommandService).autoConfirmPurchase(eq(payment.getId()), any());
    }

    @Test
    @DisplayName("자동 구매확정 대상이 없으면 처리하지 않는다")
    void autoConfirmPurchases_empty() {
        given(paymentRepository.findByStatusAndConfirmDeadlineAtBefore(eq(PaymentStatus.ESCROWED), any()))
                .willReturn(List.of());

        scheduler.autoConfirmPurchases();

        verify(paymentCommandService, never()).autoConfirmPurchase(any(), any());
    }

    private Payment newEscrowedPayment() {
        Payment payment = Payment.builder()
                .auctionId(1L)
                .buyerUserId(100L)
                .sellerUserId(200L)
                .amount(BigDecimal.valueOf(10_000))
                .pgProvider(PgProvider.TOSS_PAYMENTS)
                .status(PaymentStatus.REQUESTED)
                .paymentDeadlineAt(LocalDateTime.now().minusDays(1))
                .build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPgOrderId("order-1");
        payment.markPgPending("payment-key");
        payment.markEscrowed("payment-key", LocalDateTime.now().minusDays(8), 7);
        return payment;
    }
}
