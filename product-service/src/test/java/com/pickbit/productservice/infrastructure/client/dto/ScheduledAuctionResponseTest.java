package com.pickbit.productservice.infrastructure.client.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduledAuctionResponseTest {

    private final JsonMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    @DisplayName("auction-service의 경매 시작 시각 포맷을 역직렬화한다")
    void deserializeAuctionServiceDateTimeFormat() throws Exception {
        String json = """
                {
                    "auctionId": 8,
                    "productId": 9,
                    "startTime": "2026-06-23 09:55:00"
                }
                """;

        ScheduledAuctionResponse response = objectMapper.readValue(json, ScheduledAuctionResponse.class);

        assertThat(response.auctionId()).isEqualTo(8L);
        assertThat(response.productId()).isEqualTo(9L);
        assertThat(response.startTime()).isEqualTo(LocalDateTime.of(2026, 6, 23, 9, 55));
    }
}
