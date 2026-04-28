package com.pickbit.auctionservice.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.auctionservice.domain.OutBoxEvent;
import com.pickbit.auctionservice.infrastructure.persistence.OutBoxEventRepository;
import com.pickbit.library.event.EventBoxIdCreateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private static final String SERVICE_NAME = "auction-service";

    private final OutBoxEventRepository outBoxEventRepository;
    private final ObjectMapper objectMapper;
    private final EventBoxIdCreateService eventBoxIdCreateService;

    /**
     * 호출 측 트랜잭션 안에서 outbox 행을 기록한다.
     * Kafka Connect (Debezium) 가 binlog 를 읽어 자동으로 토픽으로 발행한다.
     */
    public void record(String entity, String aggregateId, String eventType, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutBoxEvent event = OutBoxEvent.builder()
                    .entity(entity)
                    .eventId(eventBoxIdCreateService.createEventId(SERVICE_NAME))
                    .eventType(eventType)
                    .aggregateId(aggregateId)
                    .payload(json)
                    .build();
            outBoxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Outbox payload 직렬화 실패: " + eventType, e);
        }
    }
}
