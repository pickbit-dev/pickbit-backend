package com.pickbit.auctionservice.api.dto.response;

import com.pickbit.auctionservice.domain.enums.BidStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 입찰 응답 DTO.
 *
 * @param id              입찰 ID
 * @param auctionId       소속 경매 ID
 * @param bidderNickname  입찰자 닉네임
 * @param amount          입찰 금액
 * @param bidTime         입찰 시각
 * @param bidStatus       입찰 상태. {@code ACTIVE}(현재 최고 입찰), {@code OUTBID}(밀림), {@code WINNING}(낙찰)
 */
public record BidResponse(
        Long id,
        Long auctionId,
        String bidderNickname,
        BigDecimal amount,
        LocalDateTime bidTime,
        BidStatus bidStatus
) {
}
