package com.pickbit.auctionservice.infrastructure.client;

import com.pickbit.auctionservice.exception.AuctionProductNotFoundException;
import com.pickbit.auctionservice.infrastructure.client.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private final WebClient productServiceWebClient;

    public ProductResponse getProduct(Long productId) {
        try {
            return productServiceWebClient.get()
                    .uri("/products/{id}", productId)
                    .retrieve()
                    .bodyToMono(ProductResponse.class)
                    .block();
        } catch (WebClientResponseException.NotFound e) {
            throw new AuctionProductNotFoundException(productId);
        }
    }

    public void updateProductStatus(Long productId, String status) {
        try {
            productServiceWebClient.patch()
                    .uri("/internal/products/{id}/status", productId)
                    .bodyValue(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity()
                    .block();
        } catch (Exception e) {
            log.error("product-service 상태 업데이트 실패. productId={}, status={}, error={}", productId, status, e.getMessage());
        }
    }
}
