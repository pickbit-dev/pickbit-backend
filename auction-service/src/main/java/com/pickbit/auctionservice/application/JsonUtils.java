package com.pickbit.auctionservice.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Slf4j
public class JsonUtils {

    private final JsonMapper objectMapper;

    /**
     * 객체를 JSON 문자열로 변환
     */
    public String toJson(Object object) {
        if (object == null) {
            return null;
        }

        return objectMapper.writeValueAsString(object);
    }
}
