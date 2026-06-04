package com.pickbit.paymentservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 토스페이먼츠 오류 응답입니다.
 *
 * @param code 오류 코드
 * @param message 오류 메시지
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TossErrorResponse(
        String code,
        String message
) {
}
