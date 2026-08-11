package com.pickbit.productservice.application.event;

import com.pickbit.productservice.application.InboxService;
import com.pickbit.productservice.application.ProductCommandService;
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
public class ProductStatusEventHandler implements InboxEventHandler {

    public static final String TOPIC = "Product-topic";
    private static final String UPDATE_ACTION = "UPDATE";

    private final ProductCommandService productCommandService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleUpdate(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, UPDATE_ACTION);
        }

        ProductStatusUpdateEvent event = eventHandlerSupport.deserialize(messageBody, ProductStatusUpdateEvent.class);
        validateAggregateId(aggregateId, event);

        try {
            productCommandService.updateProductStatus(event.productId(), event.status());
            inboxService.recordSuccess(eventId, TOPIC, UPDATE_ACTION, aggregateId, messageBody, eventVersion);
            log.info("상품 상태 이벤트 처리 완료. eventId={}, productId={}, status={}, reason={}, auctionId={}",
                    eventId, event.productId(), event.status(), event.reason(), event.auctionId());
        } catch (Exception e) {
            log.error("상품 상태 이벤트 처리 실패. eventId={}, productId={}", eventId, event.productId(), e);
            inboxService.recordFailure(eventId, TOPIC, UPDATE_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, UPDATE_ACTION, e);
        }
    }

    private void validateAggregateId(String aggregateId, ProductStatusUpdateEvent event) {
        if (event.productId() == null || event.status() == null) {
            throw new KafkaInvalidMessageException("productId와 status는 필수입니다.");
        }
        String expectedAggregateId = "Product:" + event.productId();
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload productId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public Set<String> actions() {
        return Set.of(UPDATE_ACTION);
    }

    /**
     * action 에 맞는 처리로 넘깁니다. Kafka 리스너와 인박스 재처리 스케줄러가 같은 진입점을 씁니다.
     */
    @Override
    public void handle(String action, String eventId, String aggregateId, String messageBody, Long eventVersion) {
        switch (action) {
            case UPDATE_ACTION -> handleUpdate(eventId, aggregateId, messageBody, eventVersion);
            default -> throw new IllegalArgumentException("지원하지 않는 action: " + action);
        }
    }
}
