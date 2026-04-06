package com.pickbit.auctionservice.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 경매 생성 요청 DTO.
 *
 * @param productId            경매 대상 상품 ID (product-service 참조)
 * @param startingPrice        시작가. 첫 입찰 시 이 금액 이상이어야 합니다.
 * @param buyNowPrice          즉시 구매가 (선택). 이 금액 이상의 입찰이 들어오면 경매가 즉시 종료됩니다.
 * @param minimumBidIncrement  최소 입찰 단위. 현재가에 이 금액을 더한 값 이상으로 입찰해야 합니다.
 * @param startTime            경매 시작 시각. 반드시 미래 시각이어야 합니다.
 * @param endTime              경매 종료 시각. 반드시 {@code startTime}보다 늦어야 합니다.
 */
public record AuctionCreateRequest(
        @NotNull(message = "상품 ID는 필수입니다.")
        Long productId,

        @NotNull(message = "시작가는 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "시작가는 0보다 커야 합니다.")
        BigDecimal startingPrice,

        @DecimalMin(value = "0", inclusive = false, message = "즉시 구매가는 0보다 커야 합니다.")
        BigDecimal buyNowPrice,

        @NotNull(message = "최소 입찰 단위는 필수입니다.")
        @DecimalMin(value = "0", inclusive = false, message = "최소 입찰 단위는 0보다 커야 합니다.")
        BigDecimal minimumBidIncrement,

        @NotNull(message = "경매 시작 시각은 필수입니다.")
        @Future(message = "경매 시작 시각은 미래여야 합니다.")
        LocalDateTime startTime,

        @NotNull(message = "경매 종료 시각은 필수입니다.")
        @Future(message = "경매 종료 시각은 미래여야 합니다.")
        LocalDateTime endTime
) {
}
