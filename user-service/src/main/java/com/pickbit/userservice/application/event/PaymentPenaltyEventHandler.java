package com.pickbit.userservice.application.event;

import com.pickbit.userservice.application.InboxService;
import com.pickbit.userservice.domain.User;
import com.pickbit.userservice.domain.UserPenalty;
import com.pickbit.userservice.domain.enums.PenaltyReason;
import com.pickbit.userservice.exception.UserNotFoundException;
import com.pickbit.userservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.userservice.exception.kafka.KafkaInvalidMessageException;
import com.pickbit.userservice.exception.kafka.KafkaSyncException;
import com.pickbit.userservice.infrastructure.persistence.UserPenaltyRepository;
import com.pickbit.userservice.infrastructure.persistence.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPenaltyEventHandler {

    public static final String TOPIC = "Payment-topic";
    public static final String FAILED_NO_PAYMENT_ACTION = "FAILED_NO_PAYMENT";
    public static final String CANCELLED_BEFORE_PAYMENT_ACTION = "CANCELLED_BEFORE_PAYMENT";

    private static final int FAILED_NO_PAYMENT_DELTA = -10;
    private static final int CANCELLED_BEFORE_PAYMENT_DELTA = -5;

    private final UserRepository userRepository;
    private final UserPenaltyRepository userPenaltyRepository;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleFailedNoPayment(String eventId, String aggregateId, String messageBody) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION);
        }

        PaymentFailedNoPaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentFailedNoPaymentEvent.class);
        validateAggregateId(aggregateId, event.paymentId(), event.buyerUserId());

        try {
            applyPenalty(eventId, aggregateId, messageBody, FAILED_NO_PAYMENT_ACTION,
                    event.buyerUserId(), PenaltyReason.PAYMENT_FAILED_NO_PAYMENT, FAILED_NO_PAYMENT_DELTA,
                    event.paymentId(), event.auctionId(), event.productId());
            log.info("미결제 만료 패널티 적용 완료. eventId={}, buyerUserId={}, delta={}",
                    eventId, event.buyerUserId(), FAILED_NO_PAYMENT_DELTA);
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, FAILED_NO_PAYMENT_ACTION, e);
        }
    }

    @Transactional
    public void handleCancelledBeforePayment(String eventId, String aggregateId, String messageBody) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION);
        }

        PaymentCancelledBeforePaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentCancelledBeforePaymentEvent.class);
        validateAggregateId(aggregateId, event.paymentId(), event.buyerUserId());

        try {
            applyPenalty(eventId, aggregateId, messageBody, CANCELLED_BEFORE_PAYMENT_ACTION,
                    event.buyerUserId(), PenaltyReason.PAYMENT_CANCELLED_BEFORE_PAYMENT, CANCELLED_BEFORE_PAYMENT_DELTA,
                    event.paymentId(), event.auctionId(), event.productId());
            log.info("결제 전 포기 패널티 적용 완료. eventId={}, buyerUserId={}, delta={}",
                    eventId, event.buyerUserId(), CANCELLED_BEFORE_PAYMENT_DELTA);
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, CANCELLED_BEFORE_PAYMENT_ACTION, e);
        }
    }

    private void applyPenalty(
            String eventId,
            String aggregateId,
            String messageBody,
            String action,
            Long buyerUserId,
            PenaltyReason reason,
            int scoreDelta,
            Long paymentId,
            Long auctionId,
            Long productId
    ) {
        User user = userRepository.findByAccountIdForUpdate(buyerUserId)
                .orElseThrow(() -> new UserNotFoundException(buyerUserId));
        int scoreAfter = user.applyTrustScoreDelta(scoreDelta);

        UserPenalty penalty = UserPenalty.builder()
                .userId(user.getId())
                .reason(reason)
                .scoreDelta(scoreDelta)
                .scoreAfter(scoreAfter)
                .sourceEventId(eventId)
                .paymentId(paymentId)
                .auctionId(auctionId)
                .productId(productId)
                .build();
        userPenaltyRepository.save(penalty);
        inboxService.recordSuccess(eventId, TOPIC, action, aggregateId, messageBody);
    }

    private void validateAggregateId(String aggregateId, Long paymentId, Long buyerUserId) {
        if (paymentId == null || buyerUserId == null) {
            throw new KafkaInvalidMessageException("paymentId와 buyerUserId는 필수입니다.");
        }
        String expectedAggregateId = "Payment:" + paymentId;
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload paymentId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }
}
