package com.pickbit.auctionservice.infrastructure.client.dto;

/**
 * product-service 내부 상품 경매 예약 요청 DTO.
 *
 * @param sellerUserId 경매를 생성하려는 판매자 계정 ID
 */
public record ProductAuctionReservationRequest(
        Long sellerUserId
) {
}
