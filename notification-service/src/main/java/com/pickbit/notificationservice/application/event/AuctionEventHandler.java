package com.pickbit.notificationservice.application.event;

import com.pickbit.notificationservice.application.InboxService;
import com.pickbit.notificationservice.application.NotificationCommandService;
import com.pickbit.notificationservice.domain.enums.NotificationTargetType;
import com.pickbit.notificationservice.domain.enums.NotificationType;
import com.pickbit.notificationservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.notificationservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.notificationservice.exception.kafka.KafkaSyncException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionEventHandler {

    public static final String TOPIC = "Auction-topic";
    public static final String WON_ACTION = "WON";

    private final NotificationCommandService notificationCommandService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleWon(String eventId, String aggregateId, String messageBody) {
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
            inboxService.recordSuccess(eventId, TOPIC, WON_ACTION, aggregateId, messageBody);
            log.info("낙찰 알림 생성 완료. eventId={}, auctionId={}, buyerUserId={}", eventId, event.auctionId(), event.buyerUserId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, WON_ACTION, aggregateId, messageBody, e.getMessage());
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
}
