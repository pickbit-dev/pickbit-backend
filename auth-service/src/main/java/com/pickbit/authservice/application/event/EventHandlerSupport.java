package com.pickbit.authservice.application.event;

import com.pickbit.authservice.exception.kafka.KafkaInvalidMessageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class EventHandlerSupport {

    private final JsonMapper jsonMapper;

    public <T> T deserialize(String messageBody, Class<T> dtoClass) {
        try {
            T dto = jsonMapper.readValue(messageBody, dtoClass);
            if (dto == null) {
                throw new KafkaInvalidMessageException(dtoClass.getSimpleName(), "역직렬화 결과가 null입니다.");
            }
            return dto;
        } catch (RuntimeException e) {
            throw new KafkaInvalidMessageException(dtoClass.getSimpleName(), e.getMessage());
        }
    }
}
