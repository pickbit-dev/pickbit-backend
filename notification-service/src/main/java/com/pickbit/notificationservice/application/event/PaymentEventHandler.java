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

import java.math.BigDecimal;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEventHandler {

    public static final String TOPIC = "Payment-topic";
    public static final String ESCROWED_ACTION = "ESCROWED";
    public static final String REFUNDED_ACTION = "REFUNDED";
    public static final String FAILED_NO_PAYMENT_ACTION = "FAILED_NO_PAYMENT";

    private final NotificationCommandService notificationCommandService;
    private final InboxService inboxService;
    private final EventHandlerSupport eventHandlerSupport;

    @Transactional
    public void handleEscrowed(String eventId, String aggregateId, String messageBody) {
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
            inboxService.recordSuccess(eventId, TOPIC, ESCROWED_ACTION, aggregateId, messageBody);
            log.info("결제 완료 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, ESCROWED_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, ESCROWED_ACTION, e);
        }
    }

    @Transactional
    public void handleRefunded(String eventId, String aggregateId, String messageBody) {
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
            inboxService.recordSuccess(eventId, TOPIC, REFUNDED_ACTION, aggregateId, messageBody);
            log.info("환불 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, REFUNDED_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, REFUNDED_ACTION, e);
        }
    }

    @Transactional
    public void handleFailedNoPayment(String eventId, String aggregateId, String messageBody) {
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
            inboxService.recordSuccess(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody);
            log.info("미결제 만료 알림 생성 완료. eventId={}, paymentId={}", eventId, event.paymentId());
        } catch (Exception e) {
            inboxService.recordFailure(eventId, TOPIC, FAILED_NO_PAYMENT_ACTION, aggregateId, messageBody, e.getMessage());
            throw new KafkaSyncException(eventId, FAILED_NO_PAYMENT_ACTION, e);
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
}
