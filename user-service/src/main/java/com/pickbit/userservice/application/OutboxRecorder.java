package com.pickbit.userservice.application;

import com.pickbit.library.event.EventBoxIdCreateService;
import com.pickbit.userservice.api.dto.kafka.UserNicknameUpdatedEventDto;
import com.pickbit.userservice.domain.OutBoxEvent;
import com.pickbit.userservice.domain.User;
import com.pickbit.userservice.infrastructure.persistence.OutBoxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    public static final String USER_ENTITY = "User";
    public static final String NICKNAME_UPDATED_EVENT_TYPE = "NICKNAME_UPDATED";

    private final OutBoxEventRepository outBoxEventRepository;
    private final EventBoxIdCreateService eventBoxIdCreateService;
    private final JsonUtils jsonUtils;

    @Value("${spring.application.name:user-service}")
    private String serviceName;

    @Transactional
    public void nicknameUpdatedEvent(User user) {
        String eventId = eventBoxIdCreateService.createEventId(serviceName);
        UserNicknameUpdatedEventDto dto = new UserNicknameUpdatedEventDto(
                eventId,
                user.getAccountId(),
                user.getNickname(),
                LocalDateTime.now()
        );

        OutBoxEvent event = OutBoxEvent.builder()
                .entity(USER_ENTITY)
                .eventId(eventId)
                .eventType(NICKNAME_UPDATED_EVENT_TYPE)
                .aggregateId(USER_ENTITY + ":" + user.getAccountId())
                .payload(jsonUtils.toJson(dto))
                .build();
        outBoxEventRepository.save(event);
    }
}
