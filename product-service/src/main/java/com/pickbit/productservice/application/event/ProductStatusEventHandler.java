package com.pickbit.productservice.application.event;

import com.pickbit.productservice.application.InboxService;
import com.pickbit.productservice.application.ProductService;
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
public class ProductStatusEventHandler {

    public static final String TOPIC = "Product-topic";
    private static final String UPDATE_ACTION = "UPDATE";

    private final ProductService productService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleUpdate(String eventId, String aggregateId, String messageBody) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, UPDATE_ACTION);
        }

        ProductStatusUpdateEvent event = eventHandlerSupport.deserialize(messageBody, ProductStatusUpdateEvent.class);
        validateAggregateId(aggregateId, event);

        try {
            productService.updateProductStatus(event.productId(), event.status());
            inboxService.recordSuccess(eventId, TOPIC, UPDATE_ACTION, aggregateId, messageBody);
            log.info("상품 상태 이벤트 처리 완료. eventId={}, productId={}, status={}, reason={}, auctionId={}",
                    eventId, event.productId(), event.status(), event.reason(), event.auctionId());
        } catch (Exception e) {
            log.error("상품 상태 이벤트 처리 실패. eventId={}, productId={}", eventId, event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, UPDATE_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, UPDATE_ACTION, e);
        }
    }

    private void validateAggregateId(String aggregateId, ProductStatusUpdateEvent event) {
        if (event.productId() == null || event.status() == null) {
            throw new KafkaInvalidMessageException("productId와 status는 필수입니다.");
        }
        if (!String.valueOf(event.productId()).equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload productId가 일치하지 않습니다. key=%s, productId=%s"
                            .formatted(aggregateId, event.productId()));
        }
    }
}
