package com.pickbit.library.persistence.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import tools.jackson.databind.json.JsonMapper;

@Converter
public abstract class BaseJsonConverter<T> implements AttributeConverter<T, String> {

    private static final JsonMapper jsonMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    private final Class<T> clazz;

    protected BaseJsonConverter(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public String convertToDatabaseColumn(T attribute) {
        if (attribute == null) return null;
        try {
            return jsonMapper.writeValueAsString(attribute);
        } catch (Exception e) {
            throw new RuntimeException("JSON 변환 실패", e);
        }
    }

    @Override
    public T convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        try {
            return jsonMapper.readValue(dbData, clazz);
        } catch (Exception e) {
            throw new RuntimeException("JSON 파싱 실패", e);
        }
    }
}
