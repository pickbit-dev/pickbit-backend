package com.pickbit.paymentservice.application.settlement;

import com.pickbit.paymentservice.application.OutboxRecorder;
import com.pickbit.paymentservice.config.batch.SettlementBatchProperties;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SettlementBatchTest {

    @Mock PaymentRepository paymentRepository;
    @Mock OutboxRecorder outboxRecorder;

    private static final BigDecimal AMOUNT = new BigDecimal("50000.00");

    @Test
    @DisplayName("processor: 구매확정 결제의 PENDING 정산 금액을 계산한다")
    void processor_success() throws Exception {
        Payment payment = purchaseConfirmedPayment();
        Settlement settlement = pendingSettlement();
        SettlementBatchProcessor processor = new SettlementBatchProcessor(
                paymentRepository,
                new SettlementAmountCalculator(new SettlementBatchProperties(10, new BigDecimal("0.05"), new BigDecimal("0.02")))
        );
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));

        SettlementBatchItem item = processor.process(settlement);

        assertThat(item.success()).isTrue();
        assertThat(item.payment()).isSameAs(payment);
        assertThat(item.settlement()).isSameAs(settlement);
        assertThat(settlement.getGrossAmount()).isEqualByComparingTo("50000.00");
        assertThat(settlement.getPlatformFeeAmount()).isEqualByComparingTo("2500.00");
        assertThat(settlement.getPgFeeAmount()).isEqualByComparingTo("1000.00");
        assertThat(settlement.getNetSellerAmount()).isEqualByComparingTo("46500.00");
    }

    @Test
    @DisplayName("processor: 결제가 구매확정 상태가 아니면 실패 item 으로 반환한다")
    void processor_invalidPaymentStatus() throws Exception {
        Payment payment = escrowedPayment();
        Settlement settlement = pendingSettlement();
        SettlementBatchProcessor processor = new SettlementBatchProcessor(
                paymentRepository,
                new SettlementAmountCalculator(new SettlementBatchProperties(10, new BigDecimal("0.05"), BigDecimal.ZERO))
        );
        given(paymentRepository.findByIdForUpdate(payment.getId())).willReturn(Optional.of(payment));

        SettlementBatchItem item = processor.process(settlement);

        assertThat(item.success()).isFalse();
        assertThat(item.payment()).isNull();
        assertThat(item.failureReason()).contains("정산 가능한 결제 상태가 아닙니다");
    }

    @Test
    @DisplayName("writer: 성공 item 은 결제 RELEASED, 정산 COMPLETED, SETTLED outbox 를 발행한다")
    void writer_success() throws Exception {
        Payment payment = purchaseConfirmedPayment();
        Settlement settlement = pendingSettlement();
        SettlementBatchWriter writer = new SettlementBatchWriter(outboxRecorder);

        writer.write(Chunk.of(SettlementBatchItem.success(payment, settlement)));

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.RELEASED);
        assertThat(payment.getReleasedAt()).isNotNull();
        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.COMPLETED);
        assertThat(settlement.getSettledAt()).isNotNull();
        verify(outboxRecorder).paymentSettledEvent(payment, settlement);
    }

    @Test
    @DisplayName("writer: 실패 item 은 정산 FAILED 로 남기고 outbox 를 발행하지 않는다")
    void writer_failure() throws Exception {
        Settlement settlement = pendingSettlement();
        SettlementBatchWriter writer = new SettlementBatchWriter(outboxRecorder);

        writer.write(Chunk.of(SettlementBatchItem.failure(settlement, "invalid state")));

        assertThat(settlement.getStatus()).isEqualTo(SettlementStatus.FAILED);
        assertThat(settlement.getFailureReason()).isEqualTo("invalid state");
        verifyNoInteractions(outboxRecorder);
    }

    private Payment escrowedPayment() {
        Payment payment = Payment.builder()
                .auctionId(1L)
                .buyerUserId(100L)
                .sellerUserId(200L)
                .amount(AMOUNT)
                .pgProvider(PgProvider.TOSS_PAYMENTS)
                .status(PaymentStatus.REQUESTED)
                .paymentDeadlineAt(LocalDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(payment, "id", 1L);
        payment.assignPgOrderId("order-uuid");
        payment.markPgPending("payment-key");
        payment.markEscrowed("payment-key", LocalDateTime.now(), 7);
        return payment;
    }

    private Payment purchaseConfirmedPayment() {
        Payment payment = escrowedPayment();
        payment.markPurchaseConfirmed(LocalDateTime.now());
        return payment;
    }

    private Settlement pendingSettlement() {
        Settlement settlement = Settlement.builder()
                .paymentId(1L)
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
