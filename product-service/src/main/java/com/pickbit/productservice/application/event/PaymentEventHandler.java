package com.pickbit.productservice.application.event;

import com.pickbit.productservice.application.InboxService;
import com.pickbit.productservice.application.ProductCommandService;
import com.pickbit.productservice.domain.product.entity.enums.ProductPaymentRestoreResult;
import com.pickbit.productservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.productservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.productservice.exception.kafka.KafkaSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler {

    public static final String TOPIC = "Payment-topic";
    public static final String FAILED_NO_PAYMENT_ACTION = "FAILED_NO_PAYMENT";
    public static final String CANCELLED_BEFORE_PAYMENT_ACTION = "CANCELLED_BEFORE_PAYMENT";

    private final ProductCommandService productCommandService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleFailedNoPayment(String eventId, String aggregateId, String messageBody) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION);
        }

        PaymentFailedNoPaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentFailedNoPaymentEvent.class);
        validateAggregateId(aggregateId, event);

        try {
            ProductPaymentRestoreResult restoreResult = productCommandService.restoreAfterPaymentFailure(event.productId());
            inboxService.recordSuccess(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody);
            logRestoreResult(FAILED_NO_PAYMENT_ACTION, restoreResult, eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("미결제 만료 상품 복구 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, FAILED_NO_PAYMENT_ACTION, e);
        }
    }

    @Transactional
    public void handleCancelledBeforePayment(String eventId, String aggregateId, String messageBody) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION);
        }

        PaymentCancelledBeforePaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentCancelledBeforePaymentEvent.class);
        validateAggregateId(aggregateId, event.paymentId(), event.productId());

        try {
            ProductPaymentRestoreResult restoreResult = productCommandService.restoreAfterPaymentFailure(event.productId());
            inboxService.recordSuccess(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION, aggregateId, messageBody);
            logRestoreResult(CANCELLED_BEFORE_PAYMENT_ACTION, restoreResult, eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("결제 전 포기 상품 복구 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, CANCELLED_BEFORE_PAYMENT_ACTION, e);
        }
    }

    private void validateAggregateId(String aggregateId, PaymentFailedNoPaymentEvent event) {
        validateAggregateId(aggregateId, event.paymentId(), event.productId());
    }

    private void validateAggregateId(String aggregateId, Long paymentId, Long productId) {
        if (paymentId == null || productId == null) {
            throw new KafkaInvalidMessageException("paymentId와 productId는 필수입니다.");
        }
        String expectedAggregateId = "Payment:" + paymentId;
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload paymentId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }

    private void logRestoreResult(String action, ProductPaymentRestoreResult restoreResult, String eventId, Long paymentId, Long productId, Long auctionId) {
        switch (restoreResult) {
            case RESTORED -> log.info("결제 이벤트 상품 복구 완료. action={}, eventId={}, paymentId={}, productId={}, auctionId={}",
                    action, eventId, paymentId, productId, auctionId);
            case ALREADY_ACTIVE -> log.info("결제 이벤트 상품 복구 스킵 - 이미 ACTIVE. action={}, eventId={}, paymentId={}, productId={}, auctionId={}",
                    action, eventId, paymentId, productId, auctionId);
            case STALE_IGNORED -> log.warn("결제 이벤트 상품 복구 스킵 - 오래된 이벤트로 판단. action={}, eventId={}, paymentId={}, productId={}, auctionId={}",
                    action, eventId, paymentId, productId, auctionId);
        }
    }
}
