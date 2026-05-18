package com.pickbit.userservice.application.event;

import com.pickbit.userservice.exception.kafka.KafkaInvalidMessageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class EventHandlerSupport {

    private final ObjectMapper objectMapper;

    public <T> T deserialize(String messageBody, Class<T> dtoClass) {
        try {
            T dto = objectMapper.readValue(messageBody, dtoClass);
            if (dto == null) {
                throw new KafkaInvalidMessageException(dtoClass.getSimpleName(), "역직렬화 결과가 null입니다.");
            }
            return dto;
        } catch (RuntimeException e) {
            throw new KafkaInvalidMessageException(dtoClass.getSimpleName(), e.getMessage());
        }
    }
}
