package com.pickbit.notificationservice.infrastructure.kafka;

import com.pickbit.notificationservice.application.event.PaymentEventHandler;
import com.pickbit.notificationservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.notificationservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.notificationservice.exception.kafka.KafkaSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentEventHandler paymentEventHandler;

    @KafkaListener(
            topics = PaymentEventHandler.TOPIC,
            containerFactory = "jsonKafkaListenerContainerFactory"
    )
    public void handlePaymentMessage(
            @Header("action") String action,
            @Header("event_id") String eventId,
            @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId,
            @Payload String messageBody
    ) {
        try {
            switch (action) {
                case PaymentEventHandler.ESCROWED_ACTION -> paymentEventHandler.handleEscrowed(eventId, aggregateId, messageBody);
                case PaymentEventHandler.REFUNDED_ACTION -> paymentEventHandler.handleRefunded(eventId, aggregateId, messageBody);
                case PaymentEventHandler.FAILED_NO_PAYMENT_ACTION -> paymentEventHandler.handleFailedNoPayment(eventId, aggregateId, messageBody);
                case PaymentEventHandler.CANCELLED_BEFORE_PAYMENT_ACTION -> paymentEventHandler.handleCancelledBeforePayment(eventId, aggregateId, messageBody);
                default -> log.warn("지원하지 않는 결제 알림 action: {}", action);
            }
        } catch (KafkaDuplicateEventException e) {
            log.warn("중복 알림 이벤트 스킵: {}", e.getMessage());
        } catch (KafkaInvalidMessageException e) {
            log.error("알림 메시지 파싱 실패 - 재처리 불가: action={}, eventId={}, error={}", action, eventId, e.getMessage());
        } catch (KafkaSyncException e) {
            log.error("결제 알림 이벤트 처리 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("결제 알림 이벤트 예상치 못한 오류: action={}, eventId={}", action, eventId, e);
            throw e;
        }
    }
}
