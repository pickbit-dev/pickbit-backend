package com.pickbit.productservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
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

    public void update(String name, String description, boolean active, Integer sortOrder) {
        this.name = name;
        this.description = description;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public void updateActive(boolean active) {
        this.active = active;
    }
}
