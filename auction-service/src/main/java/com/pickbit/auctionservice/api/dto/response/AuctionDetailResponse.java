package com.pickbit.auctionservice.api.dto.response;

import com.pickbit.auctionservice.domain.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 경매 상세 응답 DTO.
 *
 * @param id                   경매 ID
 * @param productId            상품 ID (product-service 참조)
 * @param productName          상품명 (경매 생성 시점 스냅샷)
 * @param productThumbnailUrl  상품 썸네일 URL (경매 생성 시점 스냅샷, 없을 수 있음)
 * @param sellerNickname       판매자 닉네임
 * @param startingPrice        시작가
 * @param currentPrice         현재 최고 입찰가
 * @param buyNowPrice          즉시 구매가 (설정하지 않은 경우 null)
 * @param minimumBidIncrement  최소 입찰 단위
 * @param auctionStatus        경매 상태
 * @param startTime            경매 시작 시각
 * @param endTime              경매 종료 시각
 * @param winnerNickname       낙찰자 닉네임 (경매 종료 전 null)
 * @param finalPrice           낙찰가 (경매 종료 전 null)
 * @param createdAt            경매 등록 일시
 * @param updatedAt            경매 최종 수정 일시
 */
public record AuctionDetailResponse(
        Long id,
        Long productId,
        String productName,
        String productThumbnailUrl,
        String sellerNickname,
        BigDecimal startingPrice,
        BigDecimal currentPrice,
        BigDecimal buyNowPrice,
        BigDecimal minimumBidIncrement,
        AuctionStatus auctionStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String winnerNickname,
        BigDecimal finalPrice,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
