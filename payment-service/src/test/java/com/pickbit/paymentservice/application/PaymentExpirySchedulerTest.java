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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentExpirySchedulerTest {

    @Mock PaymentRepository paymentRepository;
    @Mock OutboxRecorder outboxRecorder;

    @InjectMocks PaymentExpiryScheduler scheduler;

    @Test
    @DisplayName("REQUESTED 와 PG_PENDING 상태의 만료된 결제를 모두 FAILED 로 전이하고 outbox 발행한다")
    void expirePayments() {
        Payment requested = newPayment(PaymentStatus.REQUESTED);
        Payment pgPending = newPayment(PaymentStatus.PG_PENDING);
        given(paymentRepository.findByStatusAndPaymentDeadlineAtBefore(eq(PaymentStatus.REQUESTED), any()))
                .willReturn(List.of(requested));
        given(paymentRepository.findByStatusAndPaymentDeadlineAtBefore(eq(PaymentStatus.PG_PENDING), any()))
                .willReturn(List.of(pgPending));

        scheduler.expirePayments();

        assertThat(requested.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(pgPending.getStatus()).isEqualTo(PaymentStatus.FAILED);
        verify(outboxRecorder, times(2)).paymentFailedNoPaymentEvent(any());
    }

    @Test
    @DisplayName("만료된 결제가 없으면 outbox 발행 없음")
    void expirePayments_empty() {
        given(paymentRepository.findByStatusAndPaymentDeadlineAtBefore(any(), any()))
                .willReturn(List.of());

        scheduler.expirePayments();

        verify(outboxRecorder, times(0)).paymentFailedNoPaymentEvent(any());
    }

    private Payment newPayment(PaymentStatus status) {
        return Payment.builder()
                .auctionId(1L)
                .buyerUserId(100L)
                .sellerUserId(200L)
                .amount(BigDecimal.valueOf(10_000))
                .pgProvider(PgProvider.TOSS_PAYMENTS)
                .status(status)
                .paymentDeadlineAt(LocalDateTime.now().minusMinutes(1))
                .build();
    }
}
