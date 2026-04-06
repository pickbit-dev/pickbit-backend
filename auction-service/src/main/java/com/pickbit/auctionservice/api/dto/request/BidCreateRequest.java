package com.pickbit.auctionservice.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * 입찰 요청 DTO.
 *
 * @param bidAmount 입찰 금액. 현재가 + 최소 입찰 단위 이상이어야 합니다.
 *                  첫 입찰의 경우 시작가 이상이어야 합니다.
 */
public record BidCreateRequest(
        @NotNull(message = "입찰 금액은 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "입찰 금액은 0보다 커야 합니다.")
        BigDecimal bidAmount
) {
}
