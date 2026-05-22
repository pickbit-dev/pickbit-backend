package com.pickbit.auctionservice.infrastructure.client.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * product-service에서 조회한 상품 정보 응답 DTO.
 *
 * <p>auction-service의 Feign 클라이언트를 통해 수신되는 상품 데이터이며,
 * 경매 생성 시 상품 유효성 검증 및 스냅샷 저장에 사용된다.
 *
 * @param id               상품 ID
 * @param name             상품명
 * @param description      상품 설명
 * @param price            상품 가격
 * @param productStatus    상품 상태 (예: {@code ACTIVE}, {@code INACTIVE})
 * @param productCondition 상품 상태 등급 (예: {@code NEW}, {@code USED})
 * @param sellerUserId     판매자 계정 ID
 * @param sellerNickname   판매자 닉네임
 * @param categoryId       카테고리 ID
 * @param categoryName     카테고리명
 * @param images           상품 이미지 목록
 */
public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        String productStatus,
        String productCondition,
        Long sellerUserId,
        String sellerNickname,
        Long categoryId,
        String categoryName,
        List<ProductImageResponse> images
) {
    /**
     * 썸네일 이미지 URL을 반환한다.
     *
     * <p>{@code images} 목록에서 {@code imageType}이 {@code "THUMBNAIL"}인 첫 번째 이미지의 URL을 반환한다.
     * 이미지가 없거나 썸네일이 없으면 {@code null}을 반환한다.
     *
     * @return 썸네일 이미지 URL, 또는 없으면 {@code null}
     */
    public String thumbnailUrl() {
        if (images == null) return null;
        return images.stream()
                .filter(img -> "THUMBNAIL".equals(img.imageType()))
                .findFirst()
                .map(ProductImageResponse::imageUrl)
                .orElse(null);
    }

    /**
     * 상품 이미지 정보 DTO.
     *
     * @param id        이미지 ID
     * @param imageUrl  이미지 URL
     * @param imageType 이미지 유형 (예: {@code THUMBNAIL}, {@code DETAIL})
     * @param sortOrder 이미지 정렬 순서
     */
    public record ProductImageResponse(
            Long id,
            String imageUrl,
            String imageType,
            Integer sortOrder
    ) {}
}
