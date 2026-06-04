package com.pickbit.auctionservice.infrastructure.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * user-service 사용자 조회 응답입니다.
 *
 * @param id 사용자 ID
 * @param nickname 사용자 닉네임
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserResponse(
        Long id,
        String nickname
) {
}
