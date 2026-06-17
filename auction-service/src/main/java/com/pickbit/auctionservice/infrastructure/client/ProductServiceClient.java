package com.pickbit.auctionservice.infrastructure.client;

import com.pickbit.auctionservice.exception.AuctionProductNotFoundException;
import com.pickbit.auctionservice.exception.ExternalServiceUnavailableException;
import com.pickbit.auctionservice.exception.InvalidProductForAuctionException;
import com.pickbit.auctionservice.infrastructure.client.dto.ProductAuctionReservationRequest;
import com.pickbit.auctionservice.infrastructure.client.dto.ProductResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private static final String CB_NAME = "productService";

    private final RestClient productServiceRestClient;

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getProductFallback")
    @Bulkhead(name = CB_NAME, type = Bulkhead.Type.SEMAPHORE)
    public ProductResponse getProduct(Long productId) {
        return productServiceRestClient.get()
                .uri("/api/internal/products/{id}", productId)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new AuctionProductNotFoundException(productId);
                })
                .body(ProductResponse.class);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "reserveForAuctionFallback")
    @Bulkhead(name = CB_NAME, type = Bulkhead.Type.SEMAPHORE)
    public void reserveForAuction(Long productId, Long sellerUserId) {
        productServiceRestClient.patch()
                .uri("/api/internal/products/{id}/auction-reservation", productId)
                .body(new ProductAuctionReservationRequest(sellerUserId))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new InvalidProductForAuctionException("상품을 경매 예약 상태로 변경할 수 없습니다. productId=" + productId);
                })
                .toBodilessEntity();
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "releaseAuctionReservationFallback")
    @Bulkhead(name = CB_NAME, type = Bulkhead.Type.SEMAPHORE)
    public void releaseAuctionReservation(Long productId) {
        productServiceRestClient.patch()
                .uri("/api/internal/products/{id}/auction-reservation/release", productId)
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unused")
    private ProductResponse getProductFallback(Long productId, Throwable t) {
        if (t instanceof AuctionProductNotFoundException ex) {
            throw ex;
        }
        log.warn("product-service getProduct 차단됨(fallback). productId={}, cause={}", productId, t.toString());
        throw new ExternalServiceUnavailableException("product-service 일시 장애");
    }

    @SuppressWarnings("unused")
    private void reserveForAuctionFallback(Long productId, Long sellerUserId, Throwable t) {
        if (t instanceof InvalidProductForAuctionException ex) {
            throw ex;
        }
        log.warn("product-service reserveForAuction 차단됨(fallback). productId={}, sellerUserId={}, cause={}",
                productId, sellerUserId, t.toString());
        throw new ExternalServiceUnavailableException("product-service 일시 장애");
    }

    @SuppressWarnings("unused")
    private void releaseAuctionReservationFallback(Long productId, Throwable t) {
        log.warn("product-service releaseAuctionReservation 실패. productId={}, cause={}", productId, t.toString());
    }
}
