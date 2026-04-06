package com.pickbit.auctionservice.api.dto.response;

import com.pickbit.auctionservice.domain.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 경매 목록 응답 DTO.
 *
 * <p>목록 조회 시 사용되는 요약 정보입니다.
 * 상세 정보가 필요한 경우 {@code GET /auctions/{id}}를 사용하세요.
 *
 * @param id                  경매 ID
 * @param productId           상품 ID (product-service 참조)
 * @param productName         상품명
 * @param productThumbnailUrl 상품 썸네일 URL
 * @param sellerNickname      판매자 닉네임
 * @param currentPrice        현재 최고 입찰가
 * @param auctionStatus       경매 상태
 * @param endTime             경매 종료 시각
 * @param createdAt           경매 등록 일시
 */
public record AuctionSummaryResponse(
        Long id,
        Long productId,
        String productName,
        String productThumbnailUrl,
        String sellerNickname,
        BigDecimal currentPrice,
        AuctionStatus auctionStatus,
        LocalDateTime endTime,
        LocalDateTime createdAt
) {
}
