package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.infrastructure.redis.MinorUnits;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * 스트림에 실린 입찰 한 건입니다. Lua 스크립트가 XADD 로 넣은 필드와 1:1로 대응합니다.
 *
 * @param auctionId      경매 ID
 * @param bidderUserId   입찰자 ID
 * @param bidderNickname 입찰자 닉네임
 * @param amount         입찰 금액
 * @param sequence       경매 내 순번
 * @param bidTime        입찰 시각
 * @param ended          이 입찰로 즉시 구매가에 도달해 경매가 끝났는지
 * @param endedSequence  종료 이벤트에 붙일 순번 ({@code ended} 가 아니면 0)
 */
public record BidRecord(
        Long auctionId,
        Long bidderUserId,
        String bidderNickname,
        BigDecimal amount,
        long sequence,
        LocalDateTime bidTime,
        boolean ended,
        long endedSequence
) {
    public static BidRecord from(Map<String, String> fields) {
        return new BidRecord(
                Long.valueOf(fields.get("auctionId")),
                Long.valueOf(fields.get("bidderUserId")),
                fields.get("bidderNickname"),
                MinorUnits.toAmount(Long.parseLong(fields.get("amountMinor"))),
                Long.parseLong(fields.get("seq")),
                LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(Long.parseLong(fields.get("bidTimeEpochMs"))),
                        ZoneId.systemDefault()),
                "1".equals(fields.get("ended")),
                Long.parseLong(fields.getOrDefault("endedSeq", "0")));
    }
}
