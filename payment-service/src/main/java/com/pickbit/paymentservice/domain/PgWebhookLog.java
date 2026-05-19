package com.pickbit.paymentservice.domain;

import com.pickbit.library.persistence.entity.BaseEntity;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Lob;
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
public class PgWebhookLog extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PgProvider provider;

    @Column(comment = "PG가 발급한 이벤트 키 (멱등 키)", nullable = false, unique = true, length = 200)
    private String pgEventId;

    @Column(comment = "이벤트 종류", nullable = false, length = 50)
    private String eventType;

    @Column(comment = "관련 PG 결제 키", length = 200)
    private String pgPaymentKey;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false, comment = "원본 페이로드")
    private String rawPayload;

    @Column(nullable = false)
    private LocalDateTime receivedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean processed = false;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    public void markProcessed() {
        this.processed = true;
        this.errorMessage = null;
    }

    public void markFailed(String error) {
        this.processed = false;
        this.errorMessage = error;
    }
}
