package com.pickbit.notificationservice.application.event;

import com.pickbit.notificationservice.application.InboxService;
import com.pickbit.notificationservice.application.NotificationCommandService;
import com.pickbit.notificationservice.domain.enums.NotificationTargetType;
import com.pickbit.notificationservice.domain.enums.NotificationType;
import com.pickbit.notificationservice.exception.kafka.KafkaDuplicateEventException;
import com.pickbit.notificationservice.exception.kafka.KafkaInvalidMessageException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 정산 완료 알림 처리 검증.
 *
 * <p>정산 이벤트(SETTLED)는 이전까지 notification-service 가 받고도
 * "지원하지 않는 action" 으로 버리고 있었다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PaymentEventHandlerTest {

    private static final String EVENT_ID = "payment-service:evt-1";
    private static final long PAYMENT_ID = 77L;
    private static final long SELLER_ID = 200L;
    private static final long BUYER_ID = 100L;

    @Mock
    NotificationCommandService notificationCommandService;

    @Mock
    InboxService inboxService;

    @Mock
    EventHandlerSupport eventHandlerSupport;

    @InjectMocks
    PaymentEventHandler handler;

    /** 실제 payment-service 가 내보내는 payload 모양. */
    private static String settledPayload() {
        return """
                {
                  "eventId": "%s",
                  "paymentId": %d,
                  "auctionId": 5,
                  "productId": 9,
                  "buyerUserId": %d,
                  "sellerUserId": %d,
                  "grossAmount": 50000.00,
                  "netSellerAmount": 47500.00,
                  "releasedAt": "2026-08-12T10:00:00"
                }
                """.formatted(EVENT_ID, PAYMENT_ID, BUYER_ID, SELLER_ID);
    }

    private void givenRealDeserialization() {
        EventHandlerSupport real = new EventHandlerSupport(JsonMapper.builder().findAndAddModules().build());
        given(eventHandlerSupport.deserialize(anyString(), any()))
                .willAnswer(invocation -> real.deserialize(invocation.getArgument(0), invocation.getArgument(1)));
    }

    @Nested
    @DisplayName("정산 완료 알림")
    class Settled {

        @Test
        @DisplayName("판매자와 구매자에게 각각 알림을 만든다")
        void createsNotificationsForBothParties() {
            givenRealDeserialization();
            given(inboxService.isAlreadyProcessed(EVENT_ID)).willReturn(false);

            handler.handleSettled(EVENT_ID, "Payment:" + PAYMENT_ID, settledPayload(), 1L);

            ArgumentCaptor<Long> recipients = ArgumentCaptor.forClass(Long.class);
            verify(notificationCommandService, times(2)).create(
                    recipients.capture(), any(), anyString(), anyString(), any(), anyLong());

            assertThat(recipients.getAllValues()).containsExactly(SELLER_ID, BUYER_ID);
        }

        @Test
        @DisplayName("판매자에게는 총 결제액이 아니라 수수료를 뺀 실수령액을 알린다")
        void tellsSellerNetAmountNotGross() {
            givenRealDeserialization();
            given(inboxService.isAlreadyProcessed(EVENT_ID)).willReturn(false);

            handler.handleSettled(EVENT_ID, "Payment:" + PAYMENT_ID, settledPayload(), 1L);

            ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
            verify(notificationCommandService, times(2)).create(
                    anyLong(), any(), anyString(), messages.capture(), any(), anyLong());

            String sellerMessage = messages.getAllValues().getFirst();
            assertThat(sellerMessage).contains("47500");
            // 총 결제액(50000)을 알려주면 판매자가 받지도 않을 금액을 보게 된다.
            assertThat(sellerMessage).doesNotContain("50000");
        }

        @Test
        @DisplayName("알림 타입과 대상은 PAYMENT_SETTLED / 결제 건이다")
        void usesSettledTypeAndPaymentTarget() {
            givenRealDeserialization();
            given(inboxService.isAlreadyProcessed(EVENT_ID)).willReturn(false);

            handler.handleSettled(EVENT_ID, "Payment:" + PAYMENT_ID, settledPayload(), 1L);

            verify(notificationCommandService, times(2)).create(
                    anyLong(),
                    org.mockito.ArgumentMatchers.eq(NotificationType.PAYMENT_SETTLED),
                    anyString(), anyString(),
                    org.mockito.ArgumentMatchers.eq(NotificationTargetType.PAYMENT),
                    org.mockito.ArgumentMatchers.eq(PAYMENT_ID));
        }

        @Test
        @DisplayName("처리 성공 시 인박스에 성공으로 기록한다")
        void recordsSuccess() {
            givenRealDeserialization();
            given(inboxService.isAlreadyProcessed(EVENT_ID)).willReturn(false);

            handler.handleSettled(EVENT_ID, "Payment:" + PAYMENT_ID, settledPayload(), 1L);

            verify(inboxService).recordSuccess(
                    org.mockito.ArgumentMatchers.eq(EVENT_ID),
                    org.mockito.ArgumentMatchers.eq(PaymentEventHandler.TOPIC),
                    org.mockito.ArgumentMatchers.eq(PaymentEventHandler.SETTLED_ACTION),
                    anyString(), anyString(), org.mockito.ArgumentMatchers.eq(1L));
        }

        @Test
        @DisplayName("이미 처리한 이벤트면 알림을 만들지 않는다")
        void skipsDuplicate() {
            given(inboxService.isAlreadyProcessed(EVENT_ID)).willReturn(true);

            assertThatThrownBy(() ->
                    handler.handleSettled(EVENT_ID, "Payment:" + PAYMENT_ID, settledPayload(), 1L))
                    .isInstanceOf(KafkaDuplicateEventException.class);

            verify(notificationCommandService, never())
                    .create(anyLong(), any(), anyString(), anyString(), any(), anyLong());
        }

        @Test
        @DisplayName("Kafka key 와 payload 의 paymentId 가 다르면 거부한다")
        void rejectsMismatchedKey() {
            givenRealDeserialization();
            given(inboxService.isAlreadyProcessed(EVENT_ID)).willReturn(false);

            assertThatThrownBy(() ->
                    handler.handleSettled(EVENT_ID, "Payment:999", settledPayload(), 1L))
                    .isInstanceOf(KafkaInvalidMessageException.class);

            verify(notificationCommandService, never())
                    .create(anyLong(), any(), anyString(), anyString(), any(), anyLong());
        }
    }

    @Nested
    @DisplayName("액션 등록")
    class ActionRegistration {

        /**
         * action 을 추가할 때는 actions(), handle() switch, Kafka 리스너 switch 세 곳을 모두 고쳐야 한다.
         * actions() 를 빠뜨리면 리스너는 동작하지만 인박스 재처리 스케줄러가 이 이벤트를 조용히 건너뛴다.
         */
        @Test
        @DisplayName("actions() 가 처리 가능한 action 을 모두 선언한다")
        void actionsCoverEveryHandledAction() {
            Set<String> declared = handler.actions();

            assertThat(declared).containsExactlyInAnyOrder(
                    PaymentEventHandler.ESCROWED_ACTION,
                    PaymentEventHandler.SETTLED_ACTION,
                    PaymentEventHandler.REFUNDED_ACTION,
                    PaymentEventHandler.FAILED_NO_PAYMENT_ACTION,
                    PaymentEventHandler.CANCELLED_BEFORE_PAYMENT_ACTION);
        }

        @Test
        @DisplayName("선언한 action 은 handle() 에서 전부 라우팅된다")
        void handleRoutesEveryDeclaredAction() {
            givenRealDeserialization();
            given(inboxService.isAlreadyProcessed(anyString())).willReturn(true);

            for (String action : handler.actions()) {
                // 라우팅되면 중복 예외까지 도달한다. default 로 빠지면 IllegalArgumentException 이 난다.
                assertThatThrownBy(() ->
                        handler.handle(action, EVENT_ID, "Payment:" + PAYMENT_ID, settledPayload(), 1L))
                        .as("action=%s", action)
                        .isInstanceOf(KafkaDuplicateEventException.class);
            }
        }

        @Test
        @DisplayName("모르는 action 은 예외로 드러낸다")
        void unknownActionFails() {
            assertThatThrownBy(() ->
                    handler.handle("UNKNOWN", EVENT_ID, "Payment:" + PAYMENT_ID, settledPayload(), 1L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
