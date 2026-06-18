package com.pickbit.paymentservice.infrastructure.client;

import com.pickbit.paymentservice.exception.PgUnavailableException;
import com.pickbit.paymentservice.exception.TossPaymentApiException;
import com.pickbit.paymentservice.infrastructure.client.dto.TossErrorResponse;
import com.pickbit.paymentservice.infrastructure.client.dto.TossPaymentResponse;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossPaymentsClient {

    private static final String CB_NAME = "tossPayments";

    private final RestClient tossPaymentsRestClient;
    private final JsonMapper jsonMapper;

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "confirmFallback")
    @Bulkhead(name = CB_NAME, type = Bulkhead.Type.SEMAPHORE)
    public TossPaymentResponse confirm(String paymentKey, String orderId, BigDecimal amount) {
        return tossPaymentsRestClient.post()
                .uri("/v1/payments/confirm")
                .body(Map.of(
                        "paymentKey", paymentKey,
                        "orderId", orderId,
                        "amount", amount
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw parseToApiException(response.getStatusCode(), response.getBody().readAllBytes());
                })
                .body(TossPaymentResponse.class);
    }

    @CircuitBreaker(name = CB_NAME, fallbackMethod = "cancelFallback")
    @Bulkhead(name = CB_NAME, type = Bulkhead.Type.SEMAPHORE)
    public TossPaymentResponse cancel(String paymentKey, String reason, BigDecimal cancelAmount) {
        return tossPaymentsRestClient.post()
                .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
                .body(buildCancelBody(reason, cancelAmount))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw parseToApiException(response.getStatusCode(), response.getBody().readAllBytes());
                })
                .body(TossPaymentResponse.class);
    }

    private Map<String, Object> buildCancelBody(String reason, BigDecimal cancelAmount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", reason);
        if (cancelAmount != null) {
            body.put("cancelAmount", cancelAmount);
        }
        return body;
    }

    private TossPaymentApiException parseToApiException(HttpStatusCode statusCode, byte[] body) {
        int status = statusCode.value();
        String raw = new String(body, StandardCharsets.UTF_8);
        if (!StringUtils.hasText(raw)) {
            return new TossPaymentApiException("EMPTY_RESPONSE", "토스 결제 API 오류 응답이 비어 있습니다.", status, raw);
        }
        try {
            TossErrorResponse err = jsonMapper.readValue(body, TossErrorResponse.class);
            String code = StringUtils.hasText(err.code()) ? err.code() : "UNKNOWN";
            String message = StringUtils.hasText(err.message()) ? err.message() : raw;
            return new TossPaymentApiException(code, message, status, raw);
        } catch (Exception e) {
            return new TossPaymentApiException("PARSE_FAILED", raw, status, raw);
        }
    }

    @SuppressWarnings("unused")
    private TossPaymentResponse confirmFallback(String paymentKey, String orderId, BigDecimal amount, Throwable t) {
        if (t instanceof TossPaymentApiException ex) {
            throw ex;
        }
        log.warn("toss confirm 차단됨(fallback). orderId={}, cause={}", orderId, t.toString());
        throw new PgUnavailableException("토스 결제 API 일시 장애");
    }

    @SuppressWarnings("unused")
    private TossPaymentResponse cancelFallback(String paymentKey, String reason, BigDecimal cancelAmount, Throwable t) {
        if (t instanceof TossPaymentApiException ex) {
            throw ex;
        }
        log.warn("toss cancel 차단됨(fallback). paymentKey={}, cause={}", paymentKey, t.toString());
        throw new PgUnavailableException("토스 결제 API 일시 장애");
    }
}
