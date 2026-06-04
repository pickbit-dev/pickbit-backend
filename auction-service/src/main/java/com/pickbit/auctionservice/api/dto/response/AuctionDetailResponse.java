package com.pickbit.auctionservice.api.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 경매 상세 조회 응답입니다.
 *
 * @param id 경매 ID
 * @param productId 상품 ID
 * @param productName 상품명 스냅샷
 * @param productThumbnailUrl 상품 썸네일 URL 스냅샷
 * @param sellerNickname 판매자 닉네임
 * @param startingPrice 경매 시작가
 * @param currentPrice 현재 최고 입찰가
 * @param buyNowPrice 즉시 구매가
 * @param minimumBidIncrement 최소 입찰 단위
 * @param auctionStatus 경매 상태
 * @param startTime 경매 시작 시각
 * @param endTime 경매 종료 시각
 * @param winnerNickname 낙찰자 닉네임
 * @param finalPrice 최종 낙찰가
 * @param createdAt 경매 생성 일시
 * @param updatedAt 경매 수정 일시
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

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime startTime,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime endTime,

        String winnerNickname,
        BigDecimal finalPrice,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime createdAt,

        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
        LocalDateTime updatedAt
) {
}
