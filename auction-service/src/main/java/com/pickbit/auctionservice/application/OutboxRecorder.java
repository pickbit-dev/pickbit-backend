package com.pickbit.auctionservice.application;

import com.pickbit.auctionservice.api.dto.kafka.auction.KafkaAuctionWonDto;
import com.pickbit.auctionservice.api.dto.kafka.product.KafkaProductStatusUpdateDto;
import com.pickbit.auctionservice.domain.Auction;
import com.pickbit.auctionservice.domain.Bid;
import com.pickbit.auctionservice.domain.OutBoxEvent;
import com.pickbit.auctionservice.infrastructure.persistence.OutBoxEventRepository;
import com.pickbit.library.event.EventBoxIdCreateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private static final String PRODUCT_ENTITY = "Product";
    private static final String AUCTION_ENTITY = "Auction";
    private static final String UPDATE_EVENT_TYPE = "UPDATE";
    private static final String AUCTION_WON_EVENT_TYPE = "WON";

    private final OutBoxEventRepository outBoxEventRepository;
    private final EventBoxIdCreateService eventBoxIdCreateService;
    private final JsonUtils jsonUtils;

    @Value("${spring.application.name:auction-service}")
    private String serviceName;

    /**
     * 호출 측 트랜잭션 안에서 outbox 행을 기록한다.
     * Kafka Connect (Debezium) 가 binlog 를 읽어 자동으로 토픽으로 발행한다.
     */
    @Transactional
    public void productStatusUpdateEvent(Long productId, String status, String reason, Long auctionId) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);

        KafkaProductStatusUpdateDto dto = KafkaProductStatusUpdateDto.builder()
                .eventId(eventId)
                .productId(productId)
                .status(status)
                .reason(reason)
                .auctionId(auctionId)
                .build();

        OutBoxEvent event = OutBoxEvent.builder()
                .entity(PRODUCT_ENTITY)
                .eventId(eventId)
                .eventType(UPDATE_EVENT_TYPE)
                .aggregateId(PRODUCT_ENTITY + ":" + productId)
                .payload(jsonUtils.toJson(dto))
                .build();

        outBoxEventRepository.save(event);
    }

    /**
     * 낙찰자가 결정되었을 때 payment-service로 결제 요청 이벤트를 발행한다.
     */
    @Transactional
    public void auctionWonEvent(Auction auction, Bid winner) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);

        KafkaAuctionWonDto dto = KafkaAuctionWonDto.builder()
                .eventId(eventId)
                .auctionId(auction.getId())
                .productId(auction.getProductId())
                .productName(auction.getProductName())
                .productThumbnailUrl(auction.getProductThumbnailUrl())
                .buyerUserId(winner.getBidderUserId())
                .buyerNickname(winner.getBidderNickname())
                .sellerUserId(auction.getSellerUserId())
                .sellerNickname(auction.getSellerNickname())
                .finalPrice(winner.getAmount())
                .build();

        OutBoxEvent event = OutBoxEvent.builder()
                .entity(AUCTION_ENTITY)
                .eventId(eventId)
                .eventType(AUCTION_WON_EVENT_TYPE)
                .aggregateId(AUCTION_ENTITY + ":" + auction.getId())
                .payload(jsonUtils.toJson(dto))
                .build();

        outBoxEventRepository.save(event);
    }
}
