package com.pickbit.productservice.infrastructure.client;

import com.pickbit.productservice.infrastructure.client.dto.ScheduledAuctionResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuctionServiceClient {

    private final RestClient auctionServiceRestClient;

    public LocalDateTime getScheduledAuctionStartTime(Long productId) {
        try {
            ScheduledAuctionResponse response = auctionServiceRestClient.get()
                    .uri("/api/internal/auctions/products/{productId}/scheduled", productId)
                    .retrieve()
                    .onStatus(status -> status == HttpStatus.NOT_FOUND, (request, httpResponse) -> {
                        throw new ScheduledAuctionNotFoundException();
                    })
                    .onStatus(HttpStatusCode::isError, (request, httpResponse) -> {
                        throw new RestClientException("auction-service scheduled auction 조회 실패");
                    })
                    .body(ScheduledAuctionResponse.class);

            return response != null ? response.startTime() : null;
        } catch (ScheduledAuctionNotFoundException e) {
            return null;
        } catch (RestClientException e) {
            log.warn("auction-service 예정 경매 조회 실패. productId={}, cause={}", productId, e.toString());
            return null;
        }
    }

    private static class ScheduledAuctionNotFoundException extends RuntimeException {
    }
}
