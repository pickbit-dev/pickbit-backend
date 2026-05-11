package com.pickbit.library.logging.logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.library.logging.config.FilterProperties;
import com.pickbit.library.logging.support.LoggingUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.nio.charset.StandardCharsets;

/**
 * Service responsible for logging HTTP request information
 */
@Slf4j
public class RequestLogger {

    private final ObjectMapper objectMapper;

    public RequestLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Log the start of a request (before processing)
     */
    public void logRequestStart(HttpServletRequest request) {

        // Content-Type 설정
        if (request.getContentType() != null) {
            MDC.put("content_type", request.getContentType());
        }

        String uri = request.getRequestURI();
        String queryString = request.getQueryString();

        // 쿼리 파라미터가 있으면 URI에 연결
        String fullUrl = LoggingUtils.getFullUrl(uri, queryString);

        log.info("{} {}", request.getMethod(), fullUrl);
    }

    /**
     * Log the complete request information (after processing)
     */
    public void logRequestComplete(ContentCachingRequestWrapper request, FilterProperties filterProperties) {
        // 요청 바디 처리
        if (LoggingUtils.isLoggableContentType(request.getContentType())) {
            byte[] bodyBytes = request.getContentAsByteArray();
            if (bodyBytes.length > 0) {
                MDC.put("request_size", String.valueOf(bodyBytes.length));

                // JSON 요청만 파싱 (설정된 크기 미만)
                if (request.getContentType().contains("application/json") && bodyBytes.length < filterProperties.getRequestBodyLimit()) {
                    try {
                        String requestBody = new String(bodyBytes, StandardCharsets.UTF_8);
                        JsonNode jsonNode = objectMapper.readTree(requestBody);
                        MDC.put("request_body", objectMapper.writeValueAsString(jsonNode));
                    } catch (Exception e) {
                        // 파싱 실패시 무시
                        log.debug("Failed to parse request JSON: {}", e.getMessage());
                    }
                }
            }
        }
    }
}
