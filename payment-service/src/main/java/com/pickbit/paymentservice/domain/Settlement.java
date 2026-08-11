package com.pickbit.paymentservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(indexes = {
        @Index(name = "idx_settlement_seller_status", columnList = "seller_user_id, status"),
        // 배치 리더가 PENDING 과 재시도 대상 FAILED 를 훑는다.
        @Index(name = "idx_settlement_status_retry", columnList = "status, retry_count")
})
public class Settlement extends BaseEntity {

    @Column(comment = "Payment ID", nullable = false, unique = true)
    private Long paymentId;

    /**
     * 판매자 ID. 정산 내역을 조회하려면 반드시 있어야 하는 값인데 빠져 있었습니다.
     * 이게 없어서 "내 정산 내역"을 Payment 와 조인하지 않고는 조회조차 할 수 없었습니다.
     */
    @Column(comment = "판매자 사용자 ID", nullable = false)
    private Long sellerUserId;

    @Column(comment = "경매 ID")
    private Long auctionId;

    @Column(comment = "상품 ID")
    private Long productId;

    @Column(comment = "상품명 스냅샷", length = 150)
    private String productName;

    @Column(comment = "썸네일 URL 스냅샷", length = 1000)
    private String productThumbnailUrl;

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

    /**
     * 정산 실패 후 재시도한 횟수.
     *
     * <p>예전에는 배치 리더가 PENDING 만 조회해서 FAILED 로 떨어진 정산은 영원히 재시도되지
     * 않았습니다. 이제 FAILED 도 대상에 포함하되, 계속 실패하는 건이 매 주기 배치를 잡아먹지
     * 않도록 횟수에 상한을 둡니다.
     */
    @Column(nullable = false)
    @Builder.Default
    private Integer retryCount = 0;

    public void applyAmounts(
            BigDecimal grossAmount,
            BigDecimal platformFeeAmount,
            BigDecimal pgFeeAmount,
            BigDecimal netSellerAmount
    ) {
        if (this.status == SettlementStatus.COMPLETED) {
            throw new IllegalStateException("완료된 정산 금액은 변경할 수 없습니다.");
        }
        this.grossAmount = grossAmount;
        this.platformFeeAmount = platformFeeAmount;
        this.pgFeeAmount = pgFeeAmount;
        this.netSellerAmount = netSellerAmount;
    }

    public void markCompleted(LocalDateTime now) {
        if (this.status == SettlementStatus.COMPLETED) {
            return;
        }
        this.status = SettlementStatus.COMPLETED;
        this.settledAt = now;
        this.failureReason = null;
    }

    public void markFailed(String reason) {
        this.status = SettlementStatus.FAILED;
        this.failureReason = reason;
        this.retryCount = this.retryCount + 1;
    }

    /** 판매자 본인의 정산인지 확인합니다. */
    public boolean isOwnedBy(Long userId) {
        return this.sellerUserId != null && this.sellerUserId.equals(userId);
    }
}
