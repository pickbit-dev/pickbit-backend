package com.pickbit.auctionservice.infrastructure.client;

import com.pickbit.auctionservice.exception.AuctionUserNotFoundException;
import com.pickbit.auctionservice.exception.ExternalServiceUnavailableException;
import com.pickbit.auctionservice.infrastructure.client.dto.UserResponse;
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
public class
UserServiceClient {

    private static final String CB_NAME = "userService";

    private final RestClient userServiceRestClient;

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "getByNicknameFallback")
    @Bulkhead(name = CB_NAME, type = Bulkhead.Type.SEMAPHORE)
    public UserResponse getByNickname(String nickname) {
        return userServiceRestClient.get()
                .uri("/api/internal/users/by-nickname/{nickname}", nickname)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw new AuctionUserNotFoundException(nickname);
                })
                .body(UserResponse.class);
    }

    @SuppressWarnings("unused")
    private UserResponse getByNicknameFallback(String nickname, Throwable t) {
        if (t instanceof AuctionUserNotFoundException ex) {
            throw ex;
        }
        log.warn("user-service getByNickname 차단됨(fallback). nickname={}, cause={}", nickname, t.toString());
        throw new ExternalServiceUnavailableException("user-service 일시 장애");
    }
}
