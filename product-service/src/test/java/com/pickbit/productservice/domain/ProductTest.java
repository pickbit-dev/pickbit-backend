package com.pickbit.productservice.domain;

import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTest {

    @Test
    @DisplayName("경매 완료 상품도 미결제 만료 시 ACTIVE로 복구할 수 있다")
    void releaseFromAuction_completed() {
        Product product = Product.builder()
                .name("아이폰")
                .description("설명")
                .startingPrice(BigDecimal.valueOf(10_000))
                .productStatus(ProductStatus.AUCTION_COMPLETED)
                .productCondition(ProductCondition.GOOD)
                .sellerUserId(1L)
                .sellerNickname("seller")
                .build();

        product.releaseFromAuction();

        assertThat(product.getProductStatus()).isEqualTo(ProductStatus.ACTIVE);
    }
}
