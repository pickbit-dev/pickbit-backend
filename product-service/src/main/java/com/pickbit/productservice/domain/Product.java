package com.pickbit.productservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import com.pickbit.productservice.domain.product.entity.enums.ListingType;
import com.pickbit.productservice.domain.product.entity.enums.ProductCondition;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Column(comment = "상품명", nullable = false, length = 150)
    private String name;

    @Column(comment = "상품 설명", nullable = false, length = 2000)
    private String description;

    @Column(comment = "상품 가격", nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(comment = "상품 상태", nullable = false, length = 30)
    private ProductStatus productStatus;

    @Enumerated(EnumType.STRING)
    @Column(comment = "상품 컨디션", nullable = false, length = 30)
    private ProductCondition productCondition;

    @Enumerated(EnumType.STRING)
    @Column(comment = "판매 유형", nullable = false, length = 30)
    private ListingType listingType;

    @Column(comment = "판매자 닉네임", nullable = false, length = 50)
    private String sellerNickname;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(comment = "카테고리 ID")
    private Category category;

    @OrderBy("sortOrder asc, id asc")
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ProductImage> images = new ArrayList<>();

    public void update(
            String name,
            String description,
            BigDecimal price,
            ProductStatus status,
            ProductCondition condition,
            ListingType listingType,
            Category category
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.productStatus = status;
        this.productCondition = condition;
        this.listingType = listingType;
        this.category = category;
    }

    public void updateStatus(ProductStatus status) {
        this.productStatus = status;
    }

    public void replaceImages(List<ProductImage> images) {
        this.images.clear();
        if (images == null) {
            return;
        }
        images.forEach(this::addImage);
    }

    public void addImage(ProductImage image) {
        image.assignProduct(this);
        this.images.add(image);
    }
}
