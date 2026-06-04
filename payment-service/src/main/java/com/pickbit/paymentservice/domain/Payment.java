package com.pickbit.paymentservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(comment = "경매 ID", nullable = false)
    private Long auctionId;

    @Column(comment = "상품 ID 스냅샷")
    private Long productId;

    @Column(comment = "상품명 스냅샷", length = 150)
    private String productName;

    @Column(comment = "상품 썸네일 URL 스냅샷", length = 1000)
    private String productThumbnailUrl;

    @Column(comment = "구매자 사용자 ID", nullable = false)
    private Long buyerUserId;

    @Column(comment = "구매자 닉네임 스냅샷", length = 50)
    private String buyerNickname;

    @Column(comment = "판매자 사용자 ID", nullable = false)
    private Long sellerUserId;

    @Column(comment = "판매자 닉네임 스냅샷", length = 50)
    private String sellerNickname;

    @Column(comment = "결제 금액", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(comment = "PG 제공자", nullable = false, length = 30)
    private PgProvider pgProvider;

    @Column(comment = "PG 결제 키 (PG사 발급)", length = 200)
    private String pgPaymentKey;

    @Column(comment = "PG 주문 번호 (멱등 키)", length = 100, unique = true)
    private String pgOrderId;

    @Enumerated(EnumType.STRING)
    @Column(comment = "결제 상태", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(comment = "결제 데드라인 (낙찰 + 24h)", nullable = false)
    private LocalDateTime paymentDeadlineAt;

    @Column(comment = "자동 구매확정 데드라인 (결제완료 + 10d)")
    private LocalDateTime confirmDeadlineAt;

    @Column(comment = "결제(에스크로) 완료 시각")
    private LocalDateTime paidAt;

    @Column(comment = "구매확정 시각")
    private LocalDateTime confirmedAt;

    @Column(comment = "정산 완료 시각")
    private LocalDateTime releasedAt;

    @Column(comment = "환불 완료 시각")
    private LocalDateTime refundedAt;

    public void assignPgOrderId(String pgOrderId) {
        if (this.pgOrderId != null) {
            return;
        }
        this.pgOrderId = pgOrderId;
    }

    public void markPgPending(String pgPaymentKey) {
        ensureStatus(PaymentStatus.REQUESTED);
        this.pgPaymentKey = pgPaymentKey;
        this.status = PaymentStatus.PG_PENDING;
    }

    public void markEscrowed(String pgPaymentKey, LocalDateTime now, int confirmTimeoutDays) {
        ensureStatus(PaymentStatus.REQUESTED, PaymentStatus.PG_PENDING);
        this.pgPaymentKey = pgPaymentKey;
        this.paidAt = now;
        this.confirmDeadlineAt = now.plusDays(confirmTimeoutDays);
        this.status = PaymentStatus.ESCROWED;
    }

    public void markReleased(LocalDateTime now) {
        ensureStatus(PaymentStatus.ESCROWED, PaymentStatus.DISPUTED);
        this.confirmedAt = (this.confirmedAt == null) ? now : this.confirmedAt;
        this.releasedAt = now;
        this.status = PaymentStatus.RELEASED;
    }

    public void markRefunded(LocalDateTime now) {
        ensureStatus(PaymentStatus.ESCROWED, PaymentStatus.DISPUTED);
        this.refundedAt = now;
        this.status = PaymentStatus.REFUNDED;
    }

    public void markDisputed() {
        ensureStatus(PaymentStatus.ESCROWED);
        this.status = PaymentStatus.DISPUTED;
    }

    public void markFailedNoPayment() {
        ensureStatus(PaymentStatus.REQUESTED);
        this.status = PaymentStatus.FAILED;
    }

    public void markPgFailed() {
        ensureStatus(PaymentStatus.REQUESTED, PaymentStatus.PG_PENDING);
        this.status = PaymentStatus.FAILED;
    }

    public void markCancelled() {
        ensureStatus(PaymentStatus.REQUESTED);
        this.status = PaymentStatus.CANCELLED;
    }

    public void markCancelledBeforePayment() {
        ensureStatus(PaymentStatus.REQUESTED);
        this.status = PaymentStatus.CANCELLED;
    }

    private void ensureStatus(PaymentStatus... expected) {
        for (PaymentStatus s : expected) {
            if (this.status == s) return;
        }
        throw new IllegalStateException(
                "결제 상태 전이 불가. current=%s, expected=%s".formatted(this.status, Arrays.toString(expected))
        );
    }
}
