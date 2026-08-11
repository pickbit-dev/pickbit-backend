package com.pickbit.notificationservice.application.event;

import com.pickbit.notificationservice.application.InboxService;
import com.pickbit.notificationservice.application.NotificationCommandService;
import com.pickbit.notificationservice.domain.enums.NotificationTargetType;
import com.pickbit.notificationservice.domain.enums.NotificationType;
import com.pickbit.notificationservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.notificationservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.notificationservice.exception.kafka.KafkaSyncException;
import com.pickbit.library.inbox.InboxEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionEventHandler implements InboxEventHandler {

    public static final String TOPIC = "Auction-topic";
    public static final String WON_ACTION = "WON";

    private final NotificationCommandService notificationCommandService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleWon(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, WON_ACTION);
        }

        AuctionWonEvent event = eventHandlerSupport.deserialize(messageBody, AuctionWonEvent.class);
        validateAggregateId(aggregateId, event);

        try {
            notificationCommandService.create(
                    event.buyerUserId(),
                    NotificationType.AUCTION_WON,
                    "경매에 낙찰되었습니다",
                    "%s 상품에 낙찰되었습니다. 결제 기한 내에 결제를 진행해주세요.".formatted(resolveProductName(event.productName())),
                    NotificationTargetType.AUCTION,
                    event.auctionId()
            );
            inboxService.recordSuccess(eventId, TOPIC, WON_ACTION, aggregateId, messageBody, eventVersion);
            log.info("낙찰 알림 생성 완료. eventId={}, auctionId={}, buyerUserId={}", eventId, event.auctionId(), event.buyerUserId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, WON_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, WON_ACTION, e);
        }
    }

    private void validateAggregateId(String aggregateId, AuctionWonEvent event) {
        if (event.auctionId() == null || event.buyerUserId() == null) {
            throw new KafkaInvalidMessageException("auctionId와 buyerUserId는 필수입니다.");
        }
        String expectedAggregateId = "Auction:" + event.auctionId();
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload auctionId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }

    private String resolveProductName(String productName) {
        return productName == null || productName.isBlank() ? "낙찰 상품" : productName;
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public Set<String> actions() {
        return Set.of(WON_ACTION);
    }

    /**
     * action 에 맞는 처리로 넘깁니다. Kafka 리스너와 인박스 재처리 스케줄러가 같은 진입점을 씁니다.
     */
    @Override
    public void handle(String action, String eventId, String aggregateId, String messageBody, Long eventVersion) {
        switch (action) {
            case WON_ACTION -> handleWon(eventId, aggregateId, messageBody, eventVersion);
            default -> throw new IllegalArgumentException("지원하지 않는 action: " + action);
        }
    }
}
