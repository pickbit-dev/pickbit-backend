package com.pickbit.paymentservice.api.dto.response;

import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.domain.enums.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 판매자 정산 내역 응답입니다.
 *
 * @param settlementId       정산 ID
 * @param paymentId          결제 ID
 * @param auctionId          경매 ID
 * @param productId          상품 ID
 * @param productName        상품명
 * @param productThumbnailUrl 상품 썸네일
 * @param grossAmount        총 결제 금액
 * @param platformFeeAmount  플랫폼 수수료
 * @param pgFeeAmount        PG 수수료
 * @param netSellerAmount    판매자 실수령액
 * @param status             정산 상태
 * @param statusDescription  상태 설명 (화면 표시용)
 * @param settledAt          정산 완료 시각 (미완료면 null)
 * @param failureReason      실패 사유 (실패가 아니면 null)
 * @param createdAt          정산 생성 시각 (구매확정 시점)
 */
public record SettlementResponse(
        Long settlementId,
        Long paymentId,
        Long auctionId,
        Long productId,
        String productName,
        String productThumbnailUrl,
        BigDecimal grossAmount,
        BigDecimal platformFeeAmount,
        BigDecimal pgFeeAmount,
        BigDecimal netSellerAmount,
        SettlementStatus status,
        String statusDescription,
        LocalDateTime settledAt,
        String failureReason,
        LocalDateTime createdAt
) {
    public static SettlementResponse from(Settlement settlement) {
        return new SettlementResponse(
                settlement.getId(),
                settlement.getPaymentId(),
                settlement.getAuctionId(),
                settlement.getProductId(),
                settlement.getProductName(),
                settlement.getProductThumbnailUrl(),
                settlement.getGrossAmount(),
                settlement.getPlatformFeeAmount(),
                settlement.getPgFeeAmount(),
                settlement.getNetSellerAmount(),
                settlement.getStatus(),
                settlement.getStatus().getDescription(),
                settlement.getSettledAt(),
                settlement.getFailureReason(),
                settlement.getCreatedDate()
        );
    }
}
