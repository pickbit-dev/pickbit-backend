package com.pickbit.paymentservice.api.dto.response;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 상세 조회 응답입니다.
 *
 * @param paymentId 결제 ID
 * @param auctionId 경매 ID
 * @param productId 상품 ID
 * @param productName 상품명 스냅샷
 * @param productThumbnailUrl 상품 썸네일 URL 스냅샷
 * @param sellerNickname 판매자 닉네임
 * @param buyerNickname 구매자 닉네임
 * @param amount 결제 금액
 * @param status 결제 상태
 * @param pgOrderId PG 주문 ID
 * @param pgPaymentKey PG 결제 키
 * @param paymentDeadlineAt 결제 기한
 * @param paidAt 결제 완료 시각
 * @param refundedAt 환불 완료 시각
 */
public record PaymentDetailResponse(
        Long paymentId,
        Long auctionId,
        Long productId,
        String productName,
        String productThumbnailUrl,
        String sellerNickname,
        String buyerNickname,
        BigDecimal amount,
        PaymentStatus status,
        String pgOrderId,
        String pgPaymentKey,
        LocalDateTime paymentDeadlineAt,
        LocalDateTime paidAt,
        LocalDateTime refundedAt
) {
    public static PaymentDetailResponse from(Payment payment) {
        return new PaymentDetailResponse(
                payment.getId(),
                payment.getAuctionId(),
                payment.getProductId(),
                payment.getProductName(),
                payment.getProductThumbnailUrl(),
                payment.getSellerNickname(),
                payment.getBuyerNickname(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getPgOrderId(),
                payment.getPgPaymentKey(),
                payment.getPaymentDeadlineAt(),
                payment.getPaidAt(),
                payment.getRefundedAt()
        );
    }
}
