package com.pickbit.productservice.domain.category.entity;

import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "categories")
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category extends BaseEntity {

    @Column(comment = "카테고리명", nullable = false, length = 100, unique = true)
    private String name;

    @Column(comment = "카테고리 설명", nullable = false, length = 500)
    private String description;

    @Column(comment = "카테고리 활성화 여부", nullable = false)
    private boolean active;

    @Column(comment = "카테고리 정렬 순서", nullable = false)
    private Integer sortOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_category_id", comment = "상위 카테고리 ID")
    private Category parentCategory;

    public void update(String name, String description, boolean active, Integer sortOrder, Category parentCategory) {
        this.name = name;
        this.description = description;
        this.active = active;
        this.sortOrder = sortOrder;
        this.parentCategory = parentCategory;
    }

    public void updateActive(boolean active) {
        this.active = active;
    }
}
