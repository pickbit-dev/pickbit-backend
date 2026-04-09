package com.pickbit.auctionservice.infrastructure.client;

import com.pickbit.auctionservice.exception.AuctionProductNotFoundException;
import com.pickbit.auctionservice.infrastructure.client.dto.ProductResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private final RestClient productServiceRestClient;

    public ProductResponse getProduct(Long productId) {
        return productServiceRestClient.get()
                .uri("/products/{id}", productId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new AuctionProductNotFoundException(productId);
                })
                .body(ProductResponse.class);
    }

    public void updateProductStatus(Long productId, String status) {
        try {
            productServiceRestClient.patch()
                    .uri("/internal/products/{id}/status", productId)
                    .body(Map.of("status", status))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.error("product-service 상태 업데이트 실패. productId={}, status={}, error={}", productId, status, e.getMessage());
        }
    }
}
