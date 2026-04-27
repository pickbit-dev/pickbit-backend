package com.pickbit.productservice.api.dto.request;

import com.pickbit.productservice.domain.product.entity.enums.ProductSort;

import java.math.BigDecimal;

/**
 * 상품 목록 검색 조건.
 *
 * <p>{@code GET /products} 호출 시 키워드/카테고리/가격 범위/판매자/정렬을 조합해 결과를 필터링한다.
 *
 * @param keyword        상품명 또는 설명에 포함될 검색어 (선택)
 * @param categoryId     카테고리 ID (선택)
 * @param sellerNickname 판매자 닉네임 (선택)
 * @param minPrice       최소 시작가 (선택)
 * @param maxPrice       최대 시작가 (선택)
 * @param sort           정렬 옵션 (기본 {@code LATEST})
 */
public record ProductSearchCondition(
        String keyword,
        Long categoryId,
        String sellerNickname,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        ProductSort sort
) {
    public ProductSort sortOrDefault() {
        return sort == null ? ProductSort.LATEST : sort;
    }
}
