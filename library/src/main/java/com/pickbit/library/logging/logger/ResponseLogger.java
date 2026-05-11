package com.pickbit.library.logging.logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.library.logging.config.FilterProperties;
import com.pickbit.library.logging.support.LoggingUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;

/**
 * Service responsible for logging HTTP response information
 */
@Slf4j
public class ResponseLogger {

    private final ObjectMapper objectMapper;

    public ResponseLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Log response information
     */
    public void logResponse(HttpServletRequest request, ContentCachingResponseWrapper response, long duration, FilterProperties filterProperties) {

        // Set response information in MDC
        MDC.put("status", String.valueOf(response.getStatus()));
        MDC.put("duration", String.valueOf(duration));
        MDC.put("response_size", String.valueOf(response.getContentAsByteArray().length));
        MDC.put("perf", LoggingUtils.getPerformanceCategory(duration));

        // JSON 응답만 파싱 (설정된 크기 미만)
        if (response.getContentType() != null && response.getContentType().contains("application/json")) {
            byte[] responseBytes = response.getContentAsByteArray();
            if (responseBytes.length > 0 && responseBytes.length < filterProperties.getResponseBodyLimit()) {
                try {
                    String responseBody = new String(responseBytes, StandardCharsets.UTF_8);
                    JsonNode jsonNode = objectMapper.readTree(responseBody);
                    MDC.put("response_body", objectMapper.writeValueAsString(jsonNode));
                } catch (Exception e) {
                    // 파싱 실패시 무시
                }
            }
        }

        // 안전하게 request에서 정보 가져오기
        String method = request.getMethod();
        String uri = request.getRequestURI();

        // 로그 레벨별 출력
        if (response.getStatus() >= 500) {
            log.error("{} {} [{}] {}ms", method, uri, response.getStatus(), duration);
        } else if (response.getStatus() >= 400) {
            log.warn("{} {} [{}] {}ms", method, uri, response.getStatus(), duration);
        } else {
            log.info("{} {} [{}] {}ms", method, uri, response.getStatus(), duration);
        }
    }
}
