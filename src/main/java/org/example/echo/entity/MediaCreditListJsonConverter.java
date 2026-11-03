package org.example.echo.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.io.IOException;
import java.util.List;

@Converter
public class MediaCreditListJsonConverter implements AttributeConverter<List<MediaCredit>, String> {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<MediaCredit>> CREDITS = new TypeReference<>() {
    };

    @Override
    public String convertToDatabaseColumn(List<MediaCredit> values) {
        try {
            return OBJECT_MAPPER.writeValueAsString(values == null ? List.of() : values);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Unable to serialize media credits", ex);
        }
    }

    @Override
    public List<MediaCredit> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(value, CREDITS);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Unable to deserialize media credits", ex);
        }
    }
}
