package com.pickbit.auctionservice.api.dto.response;

import com.pickbit.auctionservice.domain.enums.AuctionStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 경매 실시간 이벤트 DTO.
 *
 * <p>WebSocket {@code /topic/auctions/{auctionId}} 채널로 브로드캐스트되는 메시지입니다.
 * {@code auctionStatus} 값으로 이벤트 종류를 구분합니다.
 *
 * <ul>
 *   <li>{@code ACTIVE}: 새로운 입찰 발생. {@code currentPrice}, {@code bidderNickname}, {@code bidTime} 포함</li>
 *   <li>{@code ENDED}: 경매 종료. {@code winnerNickname}, {@code finalPrice} 포함 (유찰 시 null)</li>
 * </ul>
 *
 * @param eventType      이벤트 종류. {@code AUCTION_STARTED}, {@code BID_PLACED}, {@code AUCTION_ENDED}
 * @param auctionId      경매 ID
 * @param bidId          입찰 ID (입찰 이벤트가 아니면 null)
 * @param auctionStatus  현재 경매 상태
 * @param currentPrice   현재 최고 입찰가 (유찰 종료 시 null)
 * @param bidderNickname 방금 입찰한 닉네임 (종료 이벤트 시 null)
 * @param bidTime        입찰 시각 (종료 이벤트 시 null)
 * @param winnerNickname 낙찰자 닉네임 (진행 중 또는 유찰 시 null)
 * @param finalPrice     낙찰가 (진행 중 또는 유찰 시 null)
 */
public record AuctionBidEvent(
        String eventType,
        Long auctionId,
        Long bidId,
        AuctionStatus auctionStatus,
        BigDecimal currentPrice,
        String bidderNickname,
        LocalDateTime bidTime,
        String winnerNickname,
        BigDecimal finalPrice
) {
    public static AuctionBidEvent ofStarted(Long auctionId, BigDecimal currentPrice) {
        return new AuctionBidEvent("AUCTION_STARTED", auctionId, null, AuctionStatus.ACTIVE,
                currentPrice, null, null, null, null);
    }

    /**
     * 새 입찰 발생 이벤트를 생성합니다.
     *
     * @param currentPrice   새 최고 입찰가
     * @param bidderNickname 입찰자 닉네임
     * @param bidTime        입찰 시각
     * @return 입찰 이벤트
     */
    public static AuctionBidEvent ofNewBid(BigDecimal currentPrice, String bidderNickname, LocalDateTime bidTime) {
        return ofNewBid(null, null, currentPrice, bidderNickname, bidTime);
    }

    public static AuctionBidEvent ofNewBid(
            Long auctionId,
            Long bidId,
            BigDecimal currentPrice,
            String bidderNickname,
            LocalDateTime bidTime) {
        return new AuctionBidEvent("BID_PLACED", auctionId, bidId, AuctionStatus.ACTIVE,
                currentPrice, bidderNickname, bidTime, null, null);
    }

    /**
     * 낙찰 종료 이벤트를 생성합니다.
     *
     * @param winnerNickname 낙찰자 닉네임
     * @param finalPrice     낙찰가
     * @return 낙찰 종료 이벤트
     */
    public static AuctionBidEvent ofEnded(String winnerNickname, BigDecimal finalPrice) {
        return ofEnded(null, winnerNickname, finalPrice);
    }

    public static AuctionBidEvent ofEnded(Long auctionId, String winnerNickname, BigDecimal finalPrice) {
        return new AuctionBidEvent("AUCTION_ENDED", auctionId, null, AuctionStatus.ENDED,
                finalPrice, null, null, winnerNickname, finalPrice);
    }

    /**
     * 유찰(입찰 없음) 종료 이벤트를 생성합니다.
     *
     * @return 유찰 종료 이벤트
     */
    public static AuctionBidEvent ofEndedNoBids() {
        return ofEndedNoBids(null);
    }

    public static AuctionBidEvent ofEndedNoBids(Long auctionId) {
        return new AuctionBidEvent("AUCTION_ENDED", auctionId, null, AuctionStatus.ENDED,
                null, null, null, null, null);
    }
}
