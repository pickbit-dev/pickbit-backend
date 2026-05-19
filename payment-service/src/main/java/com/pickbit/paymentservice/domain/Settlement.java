package com.pickbit.paymentservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Settlement extends BaseEntity {

    @Column(comment = "Payment ID", nullable = false, unique = true)
    private Long paymentId;

    @Column(comment = "총 결제 금액", nullable = false, precision = 19, scale = 2)
    private BigDecimal grossAmount;

    @Column(comment = "플랫폼 수수료", nullable = false, precision = 19, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(comment = "PG 수수료", nullable = false, precision = 19, scale = 2)
    private BigDecimal pgFeeAmount;

    @Column(comment = "판매자 정산액", nullable = false, precision = 19, scale = 2)
    private BigDecimal netSellerAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SettlementStatus status;

    @Column(comment = "정산 완료 시각")
    private LocalDateTime settledAt;

    @Column(columnDefinition = "TEXT", comment = "정산 실패 사유")
    private String failureReason;

    public void markCompleted(LocalDateTime now) {
        this.status = SettlementStatus.COMPLETED;
        this.settledAt = now;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = SettlementStatus.FAILED;
        this.failureReason = reason;
    }
}
