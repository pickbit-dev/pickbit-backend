package com.pickbit.userservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(comment = "아웃박스 이벤트")
public class OutBoxEvent extends BaseEntity {

    @Column(comment = "엔티티 타입")
    private String entity;

    @Column(comment = "이벤트 ID")
    private String eventId;

    @Column(comment = "이벤트 타입")
    private String eventType;

    @Column(nullable = false, comment = "Aggregate ID (Partition Key)")
    private String aggregateId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT", comment = "이벤트 페이로드 (JSON)")
    private String payload;
}
