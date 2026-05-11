package com.pickbit.authservice.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class JsonUtils {

    private final JsonMapper jsonMapper;

    public String toJson(Object object) {
        if (object == null) {
            return null;
        }
        return jsonMapper.writeValueAsString(object);
    }
}
