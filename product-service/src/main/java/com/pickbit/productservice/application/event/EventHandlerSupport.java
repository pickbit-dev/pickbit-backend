package com.pickbit.productservice.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pickbit.productservice.exception.kafka.KafkaInvalidMessageException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
        } catch (JsonProcessingException e) {
            throw new KafkaInvalidMessageException(dtoClass.getSimpleName(), e.getMessage());
        }
    }
}
