package com.pickbit.paymentservice.infrastructure.kafka;

import com.pickbit.paymentservice.application.event.AuctionWonEventHandler;
import com.pickbit.paymentservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.paymentservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.paymentservice.exception.kafka.KafkaSyncException;
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
public class AuctionEventListener {

    private final AuctionWonEventHandler auctionWonEventHandler;

    @KafkaListener(
            topics = AuctionWonEventHandler.TOPIC,
            containerFactory = "jsonKafkaListenerContainerFactory"
    )
    public void handleAuctionMessage(
            @Header("action") String action,
            @Header("event_id") String eventId,
            @Header(KafkaHeaders.RECEIVED_KEY) String aggregateId,
            @Payload String messageBody
    ) {
        try {
            if ("WON".equals(action)) {
                auctionWonEventHandler.handleWon(eventId, aggregateId, messageBody);
            } else {
                log.warn("지원하지 않는 경매 action: {}", action);
            }
        } catch (KafkaDuplicateEventException e) {
            log.warn("중복 이벤트 스킵: {}", e.getMessage());
        } catch (KafkaInvalidMessageException e) {
            log.error("메시지 파싱 실패 - 재처리 불가: action={}, eventId={}, error={}", action, eventId, e.getMessage());
        } catch (KafkaSyncException e) {
            log.error("낙찰 이벤트 동기화 실패: {}", e.getMessage());
        } catch (Exception e) {
            log.error("예상치 못한 오류: action={}, eventId={}", action, eventId, e);
        }
    }
}
