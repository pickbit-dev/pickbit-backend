package com.pickbit.auctionservice.application.event;

/**
 * 경매 캐시 삭제를 요청하는 애플리케이션 이벤트입니다.
 *
 * @param auctionId 캐시를 삭제할 경매 ID
 */
public record AuctionCacheEvictEvent(Long auctionId) {
}
