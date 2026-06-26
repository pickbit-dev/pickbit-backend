package com.pickbit.authservice.application;

import com.pickbit.authservice.api.dto.kafka.UserSignupEventDto;
import com.pickbit.authservice.domain.AuthAccount;
import com.pickbit.authservice.domain.OutBoxEvent;
import com.pickbit.authservice.domain.enums.OAuthProvider;
import com.pickbit.authservice.infrastructure.persistence.OutBoxEventRepository;
import com.pickbit.library.event.EventBoxIdCreateService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    public static final String AUTH_ACCOUNT_ENTITY = "AuthAccount";
    public static final String SIGNUP_EVENT_TYPE = "SIGNUP";

    private final OutBoxEventRepository outBoxEventRepository;
    private final EventBoxIdCreateService eventBoxIdCreateService;
    private final JsonUtils jsonUtils;

    @Value("${spring.application.name:auth-service}")
    private String serviceName;

    @Transactional
    public void signupEvent(AuthAccount account, String nickname, OAuthProvider provider) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);
        UserSignupEventDto dto = new UserSignupEventDto(
                eventId,
                account.getId(),
                account.getEmail(),
                nickname,
                provider.name(),
                account.getRole().name(),
                LocalDateTime.now()
        );

        OutBoxEvent event = OutBoxEvent.builder()
                .entity(AUTH_ACCOUNT_ENTITY)
                .eventId(eventId)
                .eventType(SIGNUP_EVENT_TYPE)
                .aggregateId(AUTH_ACCOUNT_ENTITY + ":" + account.getId())
                .payload(jsonUtils.toJson(dto))
                .build();
        outBoxEventRepository.save(event);
    }
}
