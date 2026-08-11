package com.pickbit.paymentservice.application.event;

import com.pickbit.paymentservice.api.dto.kafka.auction.KafkaAuctionWonDto;
import com.pickbit.paymentservice.application.InboxService;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.enums.PaymentStatus;
import com.pickbit.paymentservice.domain.enums.PgProvider;
import com.pickbit.paymentservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.paymentservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.paymentservice.exception.kafka.KafkaSyncException;
import com.pickbit.library.inbox.InboxEventHandler;
import com.pickbit.paymentservice.infrastructure.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionWonEventHandler implements InboxEventHandler {

    public static final String TOPIC = "Auction-topic";
    private static final String WON_ACTION = "WON";

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public Set<String> actions() {
        return Set.of(WON_ACTION);
    }

    private final PaymentRepository paymentRepository;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Value("${payment.deadline-hours:24}")
    private int paymentDeadlineHours;

    @Override
    @Transactional
    public void handle(String action, String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, WON_ACTION);
        }
        // 재처리 스케줄러가 뒤늦게 넘긴 이벤트일 수 있다. 그 사이 더 최신 이벤트가 반영됐다면 건너뛴다.
        if (inboxService.isStale(TOPIC, aggregateId, eventVersion)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, WON_ACTION);
        }

        KafkaAuctionWonDto event = eventHandlerSupport.deserialize(messageBody, KafkaAuctionWonDto.class);
        validate(aggregateId, event);

        try {
            paymentRepository.findByAuctionId(event.auctionId()).ifPresentOrElse(
                    existing -> log.warn("이미 결제 레코드가 존재합니다. auctionId={}, paymentId={}", event.auctionId(), existing.getId()),
                    () -> createPayment(event)
            );
            inboxService.recordSuccess(eventId, TOPIC, WON_ACTION, aggregateId, messageBody, eventVersion);
            log.info("낙찰 이벤트 처리 완료. eventId={}, auctionId={}", eventId, event.auctionId());
        } catch (Exception e) {
            log.error("낙찰 이벤트 처리 실패. eventId={}, auctionId={}", eventId, event.auctionId(), e);
            inboxService.recordFailure(eventId, TOPIC, WON_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, WON_ACTION, e);
        }
    }

    private void createPayment(KafkaAuctionWonDto event) {
        LocalDateTime now = LocalDateTime.now();
        Payment payment = Payment.builder()
                .auctionId(event.auctionId())
                .productId(event.productId())
                .productName(event.productName())
                .productThumbnailUrl(event.productThumbnailUrl())
                .buyerUserId(event.buyerUserId())
                .buyerNickname(event.buyerNickname())
                .sellerUserId(event.sellerUserId())
                .sellerNickname(event.sellerNickname())
                .amount(event.finalPrice())
                .pgProvider(PgProvider.TOSS_PAYMENTS)
                .status(PaymentStatus.REQUESTED)
                .paymentDeadlineAt(now.plusHours(paymentDeadlineHours))
                .build();
        paymentRepository.save(payment);
    }

    private void validate(String aggregateId, KafkaAuctionWonDto event) {
        if (event.auctionId() == null || event.productId() == null || event.buyerUserId() == null
                || event.sellerUserId() == null || event.finalPrice() == null) {
            throw new KafkaInvalidMessageException("auctionId/productId/buyerUserId/sellerUserId/finalPrice 는 모두 필수입니다.");
        }
        String expected = "Auction:" + event.auctionId();
        if (!expected.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key 와 payload auctionId 가 일치하지 않습니다. key=%s, expected=%s".formatted(aggregateId, expected)
            );
        }
    }
}
