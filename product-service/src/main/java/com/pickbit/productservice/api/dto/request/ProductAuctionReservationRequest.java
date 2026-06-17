package com.pickbit.productservice.api.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 상품 경매 예약 요청 DTO (내부 서비스 전용).
 *
 * @param sellerUserId 경매를 생성하려는 판매자 계정 ID
 */
public record ProductAuctionReservationRequest(
        @NotNull(message = "판매자 ID는 필수입니다.")
        Long sellerUserId
) {
}
