package com.pickbit.productservice.application.event;

import com.pickbit.productservice.application.InboxService;
import com.pickbit.productservice.application.ProductCommandService;
import com.pickbit.productservice.domain.product.entity.enums.ProductPaymentRestoreResult;
import com.pickbit.productservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.productservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.productservice.exception.kafka.KafkaSyncException;
import com.pickbit.library.inbox.InboxEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler implements InboxEventHandler {

    public static final String TOPIC = "Payment-topic";
    public static final String ESCROWED_ACTION = "ESCROWED";
    public static final String SETTLED_ACTION = "SETTLED";
    public static final String REFUNDED_ACTION = "REFUNDED";
    public static final String FAILED_NO_PAYMENT_ACTION = "FAILED_NO_PAYMENT";
    public static final String CANCELLED_BEFORE_PAYMENT_ACTION = "CANCELLED_BEFORE_PAYMENT";

    private final ProductCommandService productCommandService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleEscrowed(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, ESCROWED_ACTION);
        }

        PaymentEscrowedEvent event = eventHandlerSupport.deserialize(messageBody, PaymentEscrowedEvent.class);
        validateAggregateId(aggregateId, event.paymentId(), event.productId());

        try {
            productCommandService.markTradeInProgress(event.productId());
            inboxService.recordSuccess(eventId, TOPIC, ESCROWED_ACTION, aggregateId, messageBody, eventVersion);
            log.info("결제 완료 상품 거래 진행 처리 완료. eventId={}, paymentId={}, productId={}, auctionId={}",
                    eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("결제 완료 상품 거래 진행 처리 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, ESCROWED_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, ESCROWED_ACTION, e);
        }
    }

    @Transactional
    public void handleSettled(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, SETTLED_ACTION);
        }

        PaymentSettledEvent event = eventHandlerSupport.deserialize(messageBody, PaymentSettledEvent.class);
        validateAggregateId(aggregateId, event.paymentId(), event.productId());

        try {
            productCommandService.markSold(event.productId());
            inboxService.recordSuccess(eventId, TOPIC, SETTLED_ACTION, aggregateId, messageBody, eventVersion);
            log.info("정산 완료 상품 판매 완료 처리 완료. eventId={}, paymentId={}, productId={}, auctionId={}",
                    eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("정산 완료 상품 판매 완료 처리 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, SETTLED_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, SETTLED_ACTION, e);
        }
    }

    @Transactional
    public void handleRefunded(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, REFUNDED_ACTION);
        }

        PaymentRefundedEvent event = eventHandlerSupport.deserialize(messageBody, PaymentRefundedEvent.class);
        validateAggregateId(aggregateId, event.paymentId(), event.productId());

        try {
            productCommandService.deactivateAfterRefund(event.productId());
            inboxService.recordSuccess(eventId, TOPIC, REFUNDED_ACTION, aggregateId, messageBody, eventVersion);
            log.info("환불 상품 비활성화 처리 완료. eventId={}, paymentId={}, productId={}, auctionId={}",
                    eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("환불 상품 비활성화 처리 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, REFUNDED_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, REFUNDED_ACTION, e);
        }
    }

    @Transactional
    public void handleFailedNoPayment(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION);
        }

        PaymentFailedNoPaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentFailedNoPaymentEvent.class);
        validateAggregateId(aggregateId, event);

        try {
            ProductPaymentRestoreResult restoreResult = productCommandService.restoreAfterPaymentFailure(event.productId());
            inboxService.recordSuccess(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, eventVersion);
            logRestoreResult(FAILED_NO_PAYMENT_ACTION, restoreResult, eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("미결제 만료 상품 복구 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, FAILED_NO_PAYMENT_ACTION, e);
        }
    }

    @Transactional
    public void handleCancelledBeforePayment(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION);
        }

        PaymentCancelledBeforePaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentCancelledBeforePaymentEvent.class);
        validateAggregateId(aggregateId, event.paymentId(), event.productId());

        try {
            ProductPaymentRestoreResult restoreResult = productCommandService.restoreAfterPaymentFailure(event.productId());
            inboxService.recordSuccess(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION, aggregateId, messageBody, eventVersion);
            logRestoreResult(CANCELLED_BEFORE_PAYMENT_ACTION, restoreResult, eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("결제 전 포기 상품 복구 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
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

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public Set<String> actions() {
        return Set.of(ESCROWED_ACTION, SETTLED_ACTION, REFUNDED_ACTION, FAILED_NO_PAYMENT_ACTION, CANCELLED_BEFORE_PAYMENT_ACTION);
    }

    /**
     * action 에 맞는 처리로 넘깁니다. Kafka 리스너와 인박스 재처리 스케줄러가 같은 진입점을 씁니다.
     */
    @Override
    public void handle(String action, String eventId, String aggregateId, String messageBody, Long eventVersion) {
        switch (action) {
            case ESCROWED_ACTION -> handleEscrowed(eventId, aggregateId, messageBody, eventVersion);
            case SETTLED_ACTION -> handleSettled(eventId, aggregateId, messageBody, eventVersion);
            case REFUNDED_ACTION -> handleRefunded(eventId, aggregateId, messageBody, eventVersion);
            case FAILED_NO_PAYMENT_ACTION -> handleFailedNoPayment(eventId, aggregateId, messageBody, eventVersion);
            case CANCELLED_BEFORE_PAYMENT_ACTION -> handleCancelledBeforePayment(eventId, aggregateId, messageBody, eventVersion);
            default -> throw new IllegalArgumentException("지원하지 않는 action: " + action);
        }
    }
}
