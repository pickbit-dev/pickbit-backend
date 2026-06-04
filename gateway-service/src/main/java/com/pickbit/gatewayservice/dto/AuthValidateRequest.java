package com.pickbit.gatewayservice.dto;

/**
 * auth-service 토큰 검증 요청입니다.
 *
 * @param token 검증할 access token
 */
public record AuthValidateRequest(String token) {
}
