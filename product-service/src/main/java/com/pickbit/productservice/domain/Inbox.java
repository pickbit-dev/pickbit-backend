package com.pickbit.productservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inbox extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String eventId;

    @Column(unique = true, length = 120)
    private String successEventId;

    @Column(nullable = false, length = 100)
    private String topic;

    @Column(nullable = false, length = 50)
    private String action;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(columnDefinition = "TEXT")
    private String messageBody;

    @Column(nullable = false)
    private LocalDateTime processedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean success = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;
}
