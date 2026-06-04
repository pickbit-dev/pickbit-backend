package com.pickbit.auctionservice.api.dto.response;

import com.pickbit.auctionservice.domain.AuctionEvent;
import com.pickbit.auctionservice.domain.enums.AuctionEventType;
import com.pickbit.auctionservice.domain.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 저장된 경매 이벤트 조회 응답입니다.
 *
 * @param eventId 경매 이벤트 ID
 * @param eventType 경매 이벤트 타입
 * @param auctionId 경매 ID
 * @param bidId 입찰 ID
 * @param auctionStatus 이벤트 발생 시점의 경매 상태
 * @param currentPrice 이벤트 발생 시점의 현재가
 * @param bidderNickname 입찰자 닉네임
 * @param bidTime 입찰 시각
 * @param winnerNickname 낙찰자 닉네임
 * @param finalPrice 최종 낙찰가
 * @param createdAt 이벤트 저장 일시
 */
public record AuctionEventResponse(
        Long eventId,
        AuctionEventType eventType,
        Long auctionId,
        Long bidId,
        AuctionStatus auctionStatus,
        BigDecimal currentPrice,
        String bidderNickname,
        LocalDateTime bidTime,
        String winnerNickname,
        BigDecimal finalPrice,
        LocalDateTime createdAt
) {
    public static AuctionEventResponse from(AuctionEvent event) {
        return new AuctionEventResponse(
                event.getId(),
                event.getEventType(),
                event.getAuction().getId(),
                event.getBidId(),
                event.getAuctionStatus(),
                event.getCurrentPrice(),
                event.getBidderNickname(),
                event.getBidTime(),
                event.getWinnerNickname(),
                event.getFinalPrice(),
                event.getCreatedDate()
        );
    }
}
