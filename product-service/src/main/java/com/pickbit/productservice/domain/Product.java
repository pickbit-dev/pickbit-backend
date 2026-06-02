package com.pickbit.productservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
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

    @Column(comment = "상품 시작가", nullable = false, precision = 19, scale = 2)
    private BigDecimal startingPrice;

    @Enumerated(EnumType.STRING)
    @Column(comment = "상품 상태", nullable = false, length = 30)
    private ProductStatus productStatus;

    @Enumerated(EnumType.STRING)
    @Column(comment = "상품 컨디션", nullable = false, length = 30)
    private ProductCondition productCondition;

    @Column(comment = "판매자 닉네임", nullable = false, length = 50)
    private String sellerNickname;

    @Column(comment = "판매자 계정 ID")
    private Long sellerUserId;

    @Column(comment = "조회수", nullable = false)
    @Builder.Default
    private Long viewCount = 0L;

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
            ProductCondition condition,
            Category category
    ) {
        this.name = name;
        this.description = description;
        this.startingPrice = price;
        this.productCondition = condition;
        this.category = category;
    }

    public void scheduleAuction() {
        if (this.productStatus != ProductStatus.ACTIVE) {
            throw new IllegalStateException("ACTIVE 상태의 상품만 경매에 등록할 수 있습니다. 현재 상태: " + this.productStatus);
        }
        this.productStatus = ProductStatus.AUCTION_SCHEDULED;
    }

    public void startAuction() {
        if (this.productStatus != ProductStatus.AUCTION_SCHEDULED) {
            throw new IllegalStateException("AUCTION_SCHEDULED 상태의 상품만 경매를 시작할 수 있습니다. 현재 상태: " + this.productStatus);
        }
        this.productStatus = ProductStatus.IN_AUCTION;
    }

    public void releaseFromAuction() {
        if (this.productStatus == ProductStatus.ACTIVE) {
            return;
        }
        if (this.productStatus != ProductStatus.AUCTION_SCHEDULED
                && this.productStatus != ProductStatus.IN_AUCTION
                && this.productStatus != ProductStatus.AUCTION_COMPLETED) {
            throw new IllegalStateException("AUCTION_SCHEDULED, IN_AUCTION 또는 AUCTION_COMPLETED 상태의 상품만 경매에서 해제할 수 있습니다. 현재 상태: " + this.productStatus);
        }
        this.productStatus = ProductStatus.ACTIVE;
    }

    public void completeAuction() {
        if (this.productStatus == ProductStatus.AUCTION_COMPLETED) {
            return;
        }
        if (this.productStatus != ProductStatus.IN_AUCTION) {
            throw new IllegalStateException("IN_AUCTION 상태의 상품만 경매 완료 처리할 수 있습니다. 현재 상태: " + this.productStatus);
        }
        this.productStatus = ProductStatus.AUCTION_COMPLETED;
    }

    public void deactivate() {
        if (this.productStatus != ProductStatus.ACTIVE) {
            throw new IllegalStateException("ACTIVE 상태의 상품만 비활성화할 수 있습니다. 현재 상태: " + this.productStatus);
        }
        this.productStatus = ProductStatus.INACTIVE;
    }

    public void activate() {
        if (this.productStatus != ProductStatus.INACTIVE) {
            throw new IllegalStateException("INACTIVE 상태의 상품만 활성화할 수 있습니다. 현재 상태: " + this.productStatus);
        }
        this.productStatus = ProductStatus.ACTIVE;
    }

    public void delete() {
        if (this.productStatus != ProductStatus.ACTIVE && this.productStatus != ProductStatus.INACTIVE) {
            throw new IllegalStateException("ACTIVE 또는 INACTIVE 상태의 상품만 삭제할 수 있습니다. 현재 상태: " + this.productStatus);
        }
        this.productStatus = ProductStatus.DELETED;
    }

    public void increaseViewCount() {
        this.viewCount++;
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
