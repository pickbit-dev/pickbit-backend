package com.pickbit.paymentservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(indexes = {
        @Index(name = "idx_inbox_retry", columnList = "success, next_attempt_at"),
        @Index(name = "idx_inbox_version", columnList = "topic, aggregate_id, event_version")
})
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

    /**
     * 아웃박스 행 ID. 이벤트 순서를 판별하는 데 쓴다.
     *
     * <p>재처리 스케줄러가 실패한 이벤트를 나중에 다시 처리하면 Kafka 가 보장하던
     * 파티션 내 순서가 깨진다. 이미 더 최신 이벤트를 반영했다면 이 값으로 걸러낸다.
     */
    @Column
    private Long eventVersion;

    /** 재처리 시도 횟수. 상한을 넘으면 더 시도하지 않는다. */
    @Column(nullable = false)
    @Builder.Default
    private Integer attemptCount = 0;

    /** 다음 재처리 가능 시각. 실패할수록 뒤로 밀린다. */
    @Column
    private LocalDateTime nextAttemptAt;

    public void markRetrySucceeded() {
        this.success = true;
        this.successEventId = this.eventId;
        this.processedAt = LocalDateTime.now();
        this.nextAttemptAt = null;
        this.errorMessage = null;
    }

    public void markRetryFailed(String errorMessage, LocalDateTime nextAttemptAt) {
        this.attemptCount = this.attemptCount + 1;
        this.errorMessage = errorMessage;
        this.nextAttemptAt = nextAttemptAt;
        this.processedAt = LocalDateTime.now();
    }
}
