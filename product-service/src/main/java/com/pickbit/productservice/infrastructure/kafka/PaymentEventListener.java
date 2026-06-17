package com.pickbit.productservice.infrastructure.kafka;

import com.pickbit.productservice.application.event.PaymentEventHandler;
import com.pickbit.productservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.productservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.productservice.exception.kafka.KafkaSyncException;
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

    private final PaymentEventHandler eventHandler;

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
                case PaymentEventHandler.ESCROWED_ACTION -> eventHandler.handleEscrowed(eventId, aggregateId, messageBody);
                case PaymentEventHandler.SETTLED_ACTION -> eventHandler.handleSettled(eventId, aggregateId, messageBody);
                case PaymentEventHandler.REFUNDED_ACTION -> eventHandler.handleRefunded(eventId, aggregateId, messageBody);
                case PaymentEventHandler.FAILED_NO_PAYMENT_ACTION -> eventHandler.handleFailedNoPayment(eventId, aggregateId, messageBody);
                case PaymentEventHandler.CANCELLED_BEFORE_PAYMENT_ACTION -> eventHandler.handleCancelledBeforePayment(eventId, aggregateId, messageBody);
                default -> log.warn("지원하지 않는 결제 action: {}", action);
            }
        } catch (KafkaDuplicateEventException e) {
            log.warn("중복 이벤트 스킵: {}", e.getMessage());
        } catch (KafkaInvalidMessageException e) {
            log.error("메시지 파싱 실패 - 재처리 불가: action={}, eventId={}, error={}", action, eventId, e.getMessage());
        } catch (KafkaSyncException e) {
            log.error("결제 이벤트 상품 동기화 실패: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("예상치 못한 오류: action={}, eventId={}", action, eventId, e);
            throw e;
        }
    }
}
