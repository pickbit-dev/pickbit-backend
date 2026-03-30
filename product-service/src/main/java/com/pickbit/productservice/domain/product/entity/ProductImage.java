package com.pickbit.productservice.domain.product.entity;

import com.pickbit.library.persistence.entity.BaseEntity;
import com.pickbit.productservice.domain.product.entity.enums.ImageType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "product_images")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, comment = "상품 ID")
    private Product product;

    @Column(comment = "상품 이미지 URL", nullable = false, length = 1000)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(comment = "상품 이미지 유형", nullable = false, length = 30)
    private ImageType imageType;

    @Column(comment = "이미지 정렬 순서", nullable = false)
    private Integer sortOrder;

    public void assignProduct(Product product) {
        this.product = product;
    }
}
