package com.pickbit.library.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;


@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@SuperBuilder
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false, comment = "id")
    protected Long id;

    @CreatedDate
    @Column(comment = "생성 일시", nullable = false, updatable = false)
    protected LocalDateTime createdDate;

    @LastModifiedDate
    @Column(comment = "업데이트 일시", nullable = false)
    protected LocalDateTime updatedDate;


    @Transient
    public String getEntityType() {
        return this.getClass().getSimpleName();
    }
}
