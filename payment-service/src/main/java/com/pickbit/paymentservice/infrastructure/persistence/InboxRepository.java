package com.pickbit.paymentservice.infrastructure.persistence;

import com.pickbit.paymentservice.domain.Inbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InboxRepository extends JpaRepository<Inbox, Long> {

    boolean existsBySuccessEventId(String successEventId);

    /** 재처리 대상: 성공하지 않았고, 시도 횟수가 남았고, 다음 시도 시각이 지난 것. */
    @Query("""
            SELECT i FROM Inbox i
            WHERE i.success = false
              AND i.attemptCount < :maxAttempts
              AND (i.nextAttemptAt IS NULL OR i.nextAttemptAt <= :now)
              AND NOT EXISTS (SELECT 1 FROM Inbox s WHERE s.successEventId = i.eventId)
            ORDER BY i.eventVersion ASC NULLS FIRST, i.id ASC
            """)
    List<Inbox> findRetryable(@Param("maxAttempts") int maxAttempts,
                              @Param("now") LocalDateTime now,
                              Pageable pageable);

    /**
     * 같은 aggregate 에 대해 이미 더 최신(또는 같은) 버전을 성공 처리했는지 확인합니다.
     * 재처리 때문에 순서가 뒤집힌 이벤트를 걸러내는 데 쓴다.
     */
    boolean existsByTopicAndAggregateIdAndSuccessTrueAndEventVersionGreaterThanEqual(
            String topic, String aggregateId, Long eventVersion);

    Optional<Inbox> findByEventIdAndSuccessFalse(String eventId);
}
