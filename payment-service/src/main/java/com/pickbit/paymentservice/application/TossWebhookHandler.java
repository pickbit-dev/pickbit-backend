package com.pickbit.paymentservice.application;

import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.PgWebhookLog;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import com.pickbit.paymentservice.infrastructure.persistence.PgWebhookLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class TossWebhookHandler {

    private static final String STATUS_DONE = "DONE";
    private static final String STATUS_CANCELED = "CANCELED";
    private static final String STATUS_PARTIAL_CANCELED = "PARTIAL_CANCELED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_ABORTED = "ABORTED";

    private final JsonMapper jsonMapper;
    private final PgWebhookLogRepository pgWebhookLogRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxRecorder outboxRecorder;

    @Value("${payment.confirm-timeout-days:10}")
    private int confirmTimeoutDays;

    @Transactional
    public void handle(String rawBody) {
        JsonNode node = jsonMapper.readTree(rawBody);
        String eventId = extractEventId(node);
        if (eventId == null || pgWebhookLogRepository.existsByPgEventId(eventId)) {
            log.info("webhook 중복 또는 eventId 없음. eventId={}", eventId);
            return;
        }

        String status = textOrNull(node.get("status"));
        String paymentKey = textOrNull(node.get("paymentKey"));
        String orderId = textOrNull(node.get("orderId"));

        PgWebhookLog logEntry = PgWebhookLog.builder()
                .provider(PgProvider.TOSS_PAYMENTS)
                .pgEventId(eventId)
                .eventType(status == null ? "UNKNOWN" : status)
                .pgPaymentKey(paymentKey)
                .rawPayload(rawBody)
                .receivedAt(LocalDateTime.now())
                .build();
        pgWebhookLogRepository.save(logEntry);

        try {
            applyWebhook(orderId, paymentKey, status);
            logEntry.markProcessed();
        } catch (Exception e) {
            log.error("webhook 처리 실패. eventId={}, status={}", eventId, status, e);
            logEntry.markFailed(e.getMessage());
            throw e;
        }
    }

    private void applyWebhook(String orderId, String paymentKey, String status) {
        if (orderId == null || status == null) {
            log.warn("webhook payload 필수 필드 누락. orderId={}, status={}", orderId, status);
            return;
        }
        Payment payment = paymentRepository.findByPgOrderIdForUpdate(orderId).orElse(null);
        if (payment == null) {
            log.warn("webhook 대상 결제 없음. orderId={}", orderId);
            return;
        }

        switch (status) {
            case STATUS_DONE -> markEscrowedIfNeeded(payment, paymentKey);
            case STATUS_CANCELED, STATUS_PARTIAL_CANCELED -> markRefundedIfNeeded(payment, "PG_WEBHOOK_CANCELED");
            case STATUS_EXPIRED, STATUS_ABORTED -> markFailedIfNeeded(payment);
            default -> log.info("webhook 무시. orderId={}, status={}", orderId, status);
        }
    }

    private void markEscrowedIfNeeded(Payment payment, String paymentKey) {
        if (payment.getStatus() == PaymentStatus.ESCROWED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.REQUESTED && payment.getStatus() != PaymentStatus.PG_PENDING) {
            log.warn("webhook DONE 무시. paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }
        payment.markEscrowed(paymentKey, LocalDateTime.now(), confirmTimeoutDays);
        outboxRecorder.paymentEscrowedEvent(payment);
    }

    private void markRefundedIfNeeded(Payment payment, String reason) {
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.ESCROWED && payment.getStatus() != PaymentStatus.DISPUTED) {
            log.warn("webhook CANCELED 무시. paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }
        payment.markRefunded(LocalDateTime.now());
        outboxRecorder.paymentRefundedEvent(payment, reason);
    }

    private void markFailedIfNeeded(Payment payment) {
        if (payment.getStatus() == PaymentStatus.FAILED) {
            return;
        }
        if (payment.getStatus() != PaymentStatus.REQUESTED && payment.getStatus() != PaymentStatus.PG_PENDING) {
            log.warn("webhook EXPIRED 무시. paymentId={}, status={}", payment.getId(), payment.getStatus());
            return;
        }
        payment.markPgFailed();
        outboxRecorder.paymentFailedNoPaymentEvent(payment);
    }

    private String extractEventId(JsonNode node) {
        JsonNode idNode = node.get("eventId");
        if (idNode != null && !idNode.isNull()) {
            return idNode.asString();
        }
        String paymentKey = textOrNull(node.get("paymentKey"));
        String status = textOrNull(node.get("status"));
        if (paymentKey == null || status == null) return null;
        return paymentKey + ":" + status;
    }

    private String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asString();
    }
}
