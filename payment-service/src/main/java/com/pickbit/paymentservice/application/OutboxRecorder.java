package com.pickbit.paymentservice.application;

import com.pickbit.library.event.EventBoxIdCreateService;
import com.pickbit.paymentservice.api.dto.kafka.payment.KafkaPaymentCancelledBeforePaymentDto;
import com.pickbit.paymentservice.api.dto.kafka.payment.KafkaPaymentEscrowedDto;
import com.pickbit.paymentservice.api.dto.kafka.payment.KafkaPaymentFailedNoPaymentDto;
import com.pickbit.paymentservice.api.dto.kafka.payment.KafkaPaymentRefundedDto;
import com.pickbit.paymentservice.api.dto.kafka.payment.KafkaPaymentSettledDto;
import com.pickbit.paymentservice.domain.OutBoxEvent;
import com.pickbit.paymentservice.domain.Payment;
import com.pickbit.paymentservice.domain.Settlement;
import com.pickbit.paymentservice.infrastructure.persistence.OutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private static final String PAYMENT_ENTITY = "Payment";
    private static final String ESCROWED_EVENT_TYPE = "ESCROWED";
    private static final String SETTLED_EVENT_TYPE = "SETTLED";
    private static final String REFUNDED_EVENT_TYPE = "REFUNDED";
    private static final String FAILED_NO_PAYMENT_EVENT_TYPE = "FAILED_NO_PAYMENT";
    private static final String CANCELLED_BEFORE_PAYMENT_EVENT_TYPE = "CANCELLED_BEFORE_PAYMENT";

    private final OutBoxEventRepository outBoxEventRepository;
    private final EventBoxIdCreateService eventBoxIdCreateService;
    private final JsonUtils jsonUtils;

    @Value("${spring.application.name:payment-service}")
    private String serviceName;

    @Transactional
    public void paymentEscrowedEvent(Payment payment) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);

        KafkaPaymentEscrowedDto dto = KafkaPaymentEscrowedDto.builder()
                .eventId(eventId)
                .paymentId(payment.getId())
                .auctionId(payment.getAuctionId())
                .productId(payment.getProductId())
                .buyerUserId(payment.getBuyerUserId())
                .sellerUserId(payment.getSellerUserId())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .confirmDeadlineAt(payment.getConfirmDeadlineAt())
                .build();

        saveOutbox(eventId, ESCROWED_EVENT_TYPE, payment.getId(), jsonUtils.toJson(dto));
    }

    @Transactional
    public void paymentSettledEvent(Payment payment, Settlement settlement) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);

        KafkaPaymentSettledDto dto = KafkaPaymentSettledDto.builder()
                .eventId(eventId)
                .paymentId(payment.getId())
                .auctionId(payment.getAuctionId())
                .productId(payment.getProductId())
                .buyerUserId(payment.getBuyerUserId())
                .sellerUserId(payment.getSellerUserId())
                .grossAmount(settlement.getGrossAmount())
                .netSellerAmount(settlement.getNetSellerAmount())
                .releasedAt(payment.getReleasedAt())
                .build();

        saveOutbox(eventId, SETTLED_EVENT_TYPE, payment.getId(), jsonUtils.toJson(dto));
    }

    @Transactional
    public void paymentRefundedEvent(Payment payment, String reason) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);

        KafkaPaymentRefundedDto dto = KafkaPaymentRefundedDto.builder()
                .eventId(eventId)
                .paymentId(payment.getId())
                .auctionId(payment.getAuctionId())
                .productId(payment.getProductId())
                .buyerUserId(payment.getBuyerUserId())
                .sellerUserId(payment.getSellerUserId())
                .amount(payment.getAmount())
                .reason(reason)
                .refundedAt(payment.getRefundedAt())
                .build();

        saveOutbox(eventId, REFUNDED_EVENT_TYPE, payment.getId(), jsonUtils.toJson(dto));
    }

    @Transactional
    public void paymentFailedNoPaymentEvent(Payment payment) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);

        KafkaPaymentFailedNoPaymentDto dto = KafkaPaymentFailedNoPaymentDto.builder()
                .eventId(eventId)
                .paymentId(payment.getId())
                .auctionId(payment.getAuctionId())
                .productId(payment.getProductId())
                .buyerUserId(payment.getBuyerUserId())
                .sellerUserId(payment.getSellerUserId())
                .failedAt(payment.getUpdatedDate())
                .build();

        saveOutbox(eventId, FAILED_NO_PAYMENT_EVENT_TYPE, payment.getId(), jsonUtils.toJson(dto));
    }

    @Transactional
    public void paymentCancelledBeforePaymentEvent(Payment payment) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);

        KafkaPaymentCancelledBeforePaymentDto dto = KafkaPaymentCancelledBeforePaymentDto.builder()
                .eventId(eventId)
                .paymentId(payment.getId())
                .auctionId(payment.getAuctionId())
                .productId(payment.getProductId())
                .buyerUserId(payment.getBuyerUserId())
                .sellerUserId(payment.getSellerUserId())
                .cancelledAt(payment.getUpdatedDate())
                .build();

        saveOutbox(eventId, CANCELLED_BEFORE_PAYMENT_EVENT_TYPE, payment.getId(), jsonUtils.toJson(dto));
    }

    private void saveOutbox(String eventId, String eventType, Long paymentId, String payload) {
        OutBoxEvent event = OutBoxEvent.builder()
                .entity(PAYMENT_ENTITY)
                .eventId(eventId)
                .eventType(eventType)
                .aggregateId(PAYMENT_ENTITY + ":" + paymentId)
                .payload(payload)
                .build();
        outBoxEventRepository.save(event);
    }
}
