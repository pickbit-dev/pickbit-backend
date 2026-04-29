package com.pickbit.auctionservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * Debezium Outbox Event Router SMT 표준 컬럼명을 따른다.
 * id → 메시지 키, aggregatetype → 토픽, aggregateid → 파티션 키, type → 헤더, payload → 메시지 본문.
 */
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Slf4j
@SuperBuilder
@Table(comment = "아웃박스 이벤트")
public class OutBoxEvent extends BaseEntity {

    @Column(comment = "엔티티 타입")
    private String entity;

    @Column(comment = "이벤트 ID")
    private String eventId;

    @Column(comment = "이벤트 타입")
    private String eventType;

    @Column(nullable = false, length = 50, comment = "이벤트 액션")
    private String action;

    @Column(nullable = false, comment = "Aggregate ID (Partition Key)")
    private String aggregateId;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT", comment = "이벤트 페이로드 (JSON)")
    private String payload;
}
