package com.pickbit.productservice.application.event;

import com.pickbit.productservice.application.InboxService;
import com.pickbit.productservice.application.ProductCommandService;
import com.pickbit.productservice.domain.product.entity.enums.ProductStatus;
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
            productCommandService.updateProductStatus(event.productId(), ProductStatus.ACTIVE);
            inboxService.recordSuccess(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody);
            log.info("미결제 만료 상품 복구 완료. eventId={}, paymentId={}, productId={}, auctionId={}",
                    eventId, event.paymentId(), event.productId(), event.auctionId());
        } catch (Exception e) {
            log.error("미결제 만료 상품 복구 실패. eventId={}, paymentId={}, productId={}",
                    eventId, event.paymentId(), event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, FAILED_NO_PAYMENT_ACTION, e);
        }
    }

    private void validateAggregateId(String aggregateId, PaymentFailedNoPaymentEvent event) {
        if (event.paymentId() == null || event.productId() == null) {
            throw new KafkaInvalidMessageException("paymentId와 productId는 필수입니다.");
        }
        String expectedAggregateId = "Payment:" + event.paymentId();
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload paymentId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }
}
