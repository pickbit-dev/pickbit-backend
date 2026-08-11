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

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler implements InboxEventHandler {

    public static final String TOPIC = "Payment-topic";
    public static final String ESCROWED_ACTION = "ESCROWED";
    public static final String SETTLED_ACTION = "SETTLED";
    public static final String REFUNDED_ACTION = "REFUNDED";
    public static final String FAILED_NO_PAYMENT_ACTION = "FAILED_NO_PAYMENT";
    public static final String CANCELLED_BEFORE_PAYMENT_ACTION = "CANCELLED_BEFORE_PAYMENT";

    private final NotificationCommandService notificationCommandService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleEscrowed(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, ESCROWED_ACTION);
        }

        PaymentEscrowedEvent event = eventHandlerSupport.deserialize(messageBody, PaymentEscrowedEvent.class);
        validatePaymentAggregateId(aggregateId, event.paymentId());

        try {
            notificationCommandService.create(
                    event.buyerUserId(),
                    NotificationType.PAYMENT_ESCROWED,
                    "결제가 완료되었습니다",
                    "결제가 완료되었습니다. 판매자의 배송/전달을 기다려주세요.",
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            notificationCommandService.create(
                    event.sellerUserId(),
                    NotificationType.PAYMENT_ESCROWED,
                    "상품 결제가 완료되었습니다",
                    "낙찰자의 결제가 완료되었습니다. 상품 배송/전달을 준비해주세요.",
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            inboxService.recordSuccess(eventId, TOPIC, ESCROWED_ACTION, aggregateId, messageBody, eventVersion);
            log.info("결제 완료 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, ESCROWED_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, ESCROWED_ACTION, e);
        }
    }

    /**
     * 정산 완료 알림을 생성합니다.
     *
     * <p>정산 배치가 수수료를 계산해 정산을 완료하면 판매자에게 대금이 전달됩니다.
     * 판매자에게는 실수령액을, 구매자에게는 거래가 최종 종료됐음을 알립니다.
     *
     * <p>정산은 한 번 실패한 뒤 재시도로 성공할 수 있어 이 이벤트가 늦게 도착할 수 있습니다.
     * 중복 생성은 eventId 기준 인박스 검사로 막습니다.
     */
    @Transactional
    public void handleSettled(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, SETTLED_ACTION);
        }

        PaymentSettledEvent event = eventHandlerSupport.deserialize(messageBody, PaymentSettledEvent.class);
        validatePaymentAggregateId(aggregateId, event.paymentId());

        try {
            notificationCommandService.create(
                    event.sellerUserId(),
                    NotificationType.PAYMENT_SETTLED,
                    "정산이 완료되었습니다",
                    // 총 결제액이 아니라 수수료를 뺀 실수령액을 알려준다.
                    "판매 대금 정산이 완료되었습니다.%s".formatted(formatAmount(event.netSellerAmount())),
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            notificationCommandService.create(
                    event.buyerUserId(),
                    NotificationType.PAYMENT_SETTLED,
                    "거래가 최종 완료되었습니다",
                    "판매자에게 대금이 전달되어 거래가 완료되었습니다.",
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            inboxService.recordSuccess(eventId, TOPIC, SETTLED_ACTION, aggregateId, messageBody, eventVersion);
            log.info("정산 완료 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, SETTLED_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, SETTLED_ACTION, e);
        }
    }

    @Transactional
    public void handleRefunded(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, REFUNDED_ACTION);
        }

        PaymentRefundedEvent event = eventHandlerSupport.deserialize(messageBody, PaymentRefundedEvent.class);
        validatePaymentAggregateId(aggregateId, event.paymentId());

        try {
            String message = "결제가 환불되었습니다." + formatAmount(event.amount());
            notificationCommandService.create(
                    event.buyerUserId(),
                    NotificationType.PAYMENT_REFUNDED,
                    "환불이 완료되었습니다",
                    message,
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            notificationCommandService.create(
                    event.sellerUserId(),
                    NotificationType.PAYMENT_REFUNDED,
                    "거래 결제가 환불되었습니다",
                    message,
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            inboxService.recordSuccess(eventId, TOPIC, REFUNDED_ACTION, aggregateId, messageBody, eventVersion);
            log.info("환불 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, REFUNDED_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, REFUNDED_ACTION, e);
        }
    }

    @Transactional
    public void handleFailedNoPayment(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION);
        }

        PaymentFailedNoPaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentFailedNoPaymentEvent.class);
        validatePaymentAggregateId(aggregateId, event.paymentId());

        try {
            notificationCommandService.create(
                    event.buyerUserId(),
                    NotificationType.PAYMENT_FAILED_NO_PAYMENT,
                    "결제 기한이 만료되었습니다",
                    "낙찰 후 결제 기한 내에 결제가 완료되지 않았습니다.",
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            notificationCommandService.create(
                    event.sellerUserId(),
                    NotificationType.PAYMENT_FAILED_NO_PAYMENT,
                    "낙찰자의 결제 기한이 만료되었습니다",
                    "낙찰자의 결제가 완료되지 않아 상품이 다시 판매 가능 상태로 변경됩니다.",
                    NotificationTargetType.PRODUCT,
                    event.productId()
            );
            inboxService.recordSuccess(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, eventVersion);
            log.info("미결제 만료 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, FAILED_NO_PAYMENT_ACTION, e);
        }
    }

    @Transactional
    public void handleCancelledBeforePayment(String eventId, String aggregateId, String messageBody, Long eventVersion) {
        if (inboxService.isAlreadyProcessed(eventId)) {
            throw new KafkaDuplicateEventException(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION);
        }

        PaymentCancelledBeforePaymentEvent event = eventHandlerSupport.deserialize(messageBody, PaymentCancelledBeforePaymentEvent.class);
        validatePaymentAggregateId(aggregateId, event.paymentId());

        try {
            notificationCommandService.create(
                    event.buyerUserId(),
                    NotificationType.PAYMENT_CANCELLED_BEFORE_PAYMENT,
                    "결제 포기가 처리되었습니다",
                    "낙찰 결제 포기가 처리되어 신뢰점수가 차감됩니다.",
                    NotificationTargetType.PAYMENT,
                    event.paymentId()
            );
            notificationCommandService.create(
                    event.sellerUserId(),
                    NotificationType.PAYMENT_CANCELLED_BEFORE_PAYMENT,
                    "낙찰자가 결제를 포기했습니다",
                    "낙찰자가 결제를 포기하여 상품이 다시 판매 가능 상태로 변경됩니다.",
                    NotificationTargetType.PRODUCT,
                    event.productId()
            );
            inboxService.recordSuccess(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION, aggregateId, messageBody, eventVersion);
            log.info("결제 전 포기 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, CANCELLED_BEFORE_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage(), eventVersion);
            throw new KafkaSyncException(eventId, CANCELLED_BEFORE_PAYMENT_ACTION, e);
        }
    }

    private void validatePaymentAggregateId(String aggregateId, Long paymentId) {
        if (paymentId == null) {
            throw new KafkaInvalidMessageException("paymentId는 필수입니다.");
        }
        String expectedAggregateId = "Payment:" + paymentId;
        if (!expectedAggregateId.equals(aggregateId)) {
            throw new KafkaInvalidMessageException(
                    "Kafka key와 payload paymentId가 일치하지 않습니다. key=%s, expected=%s"
                            .formatted(aggregateId, expectedAggregateId));
        }
    }

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "" : " 금액: %s원".formatted(amount.stripTrailingZeros().toPlainString());
    }

    @Override
    public String topic() {
        return TOPIC;
    }

    @Override
    public Set<String> actions() {
        return Set.of(ESCROWED_ACTION, SETTLED_ACTION, REFUNDED_ACTION,
                FAILED_NO_PAYMENT_ACTION, CANCELLED_BEFORE_PAYMENT_ACTION);
    }

    /**
     * action 에 맞는 처리로 넘깁니다. Kafka 리스너와 인박스 재처리 스케줄러가 같은 진입점을 씁니다.
     */
    @Override
    public void handle(String action, String eventId, String aggregateId, String messageBody, Long eventVersion) {
        switch (action) {
            case ESCROWED_ACTION -> handleEscrowed(eventId, aggregateId, messageBody, eventVersion);
            case SETTLED_ACTION -> handleSettled(eventId, aggregateId, messageBody, eventVersion);
            case REFUNDED_ACTION -> handleRefunded(eventId, aggregateId, messageBody, eventVersion);
            case FAILED_NO_PAYMENT_ACTION -> handleFailedNoPayment(eventId, aggregateId, messageBody, eventVersion);
            case CANCELLED_BEFORE_PAYMENT_ACTION -> handleCancelledBeforePayment(eventId, aggregateId, messageBody, eventVersion);
            default -> throw new IllegalArgumentException("지원하지 않는 action: " + action);
        }
    }
}
