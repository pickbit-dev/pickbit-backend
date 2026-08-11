package com.pickbit.auctionservice.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lua 스크립트가 XADD 로 넣는 필드와 Java 파싱이 어긋나지 않는지 확인한다.
 * 필드 이름이 바뀌면 여기서 먼저 깨진다.
 */
class BidRecordTest {

    private static Map<String, String> fields() {
        Map<String, String> fields = new HashMap<>();
        fields.put("auctionId", "5");
        fields.put("bidderUserId", "42");
        fields.put("bidderNickname", "bidder42");
        fields.put("amountMinor", "1500000");
        fields.put("seq", "17");
        fields.put("bidTimeEpochMs", "1750000000000");
        fields.put("ended", "0");
        fields.put("endedSeq", "0");
        return fields;
    }

    @Test
    @DisplayName("스트림 필드를 그대로 복원한다")
    void parsesStreamFields() {
        BidRecord record = BidRecord.from(fields());

        assertThat(record.auctionId()).isEqualTo(5L);
        assertThat(record.bidderUserId()).isEqualTo(42L);
        assertThat(record.bidderNickname()).isEqualTo("bidder42");
        assertThat(record.amount()).isEqualByComparingTo(new BigDecimal("15000.00"));
        assertThat(record.sequence()).isEqualTo(17L);
        assertThat(record.bidTime()).isEqualTo(
                LocalDateTimeOf(1750000000000L));
        assertThat(record.ended()).isFalse();
        assertThat(record.endedSequence()).isZero();
    }

    @Test
    @DisplayName("즉시 구매로 종료된 입찰은 별도의 종료 순번을 가진다")
    void parsesBuyNowTermination() {
        Map<String, String> fields = fields();
        fields.put("ended", "1");
        fields.put("endedSeq", "18");

        BidRecord record = BidRecord.from(fields);

        assertThat(record.ended()).isTrue();
        assertThat(record.endedSequence()).isEqualTo(18L);
        // 입찰 이벤트와 종료 이벤트가 순번을 공유하면 누락 이벤트 복구가 깨진다.
        assertThat(record.endedSequence()).isNotEqualTo(record.sequence());
    }

    @Test
    @DisplayName("endedSeq 필드가 없어도 파싱에 실패하지 않는다")
    void toleratesMissingEndedSeq() {
        Map<String, String> fields = fields();
        fields.remove("endedSeq");

        assertThat(BidRecord.from(fields).endedSequence()).isZero();
    }

    private static java.time.LocalDateTime LocalDateTimeOf(long epochMs) {
        return java.time.LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }
}
