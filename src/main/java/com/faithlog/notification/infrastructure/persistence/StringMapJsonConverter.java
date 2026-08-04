package com.faithlog.notification.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Map;
import java.util.TreeMap;

@Converter
public class StringMapJsonConverter implements AttributeConverter<Map<String, String>, String> {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
	};

	@Override
	public String convertToDatabaseColumn(Map<String, String> attribute) {
		try {
			return OBJECT_MAPPER.writeValueAsString(attribute == null ? Map.of() : new TreeMap<>(attribute));
		} catch (Exception exception) {
			throw new IllegalArgumentException("Notification data payload cannot be serialized", exception);
		}
	}

	@Override
	public Map<String, String> convertToEntityAttribute(String databaseValue) {
		if (databaseValue == null || databaseValue.isBlank()) {
			return Map.of();
		}
		try {
			return Map.copyOf(OBJECT_MAPPER.readValue(databaseValue, MAP_TYPE));
		} catch (Exception exception) {
			throw new IllegalArgumentException("Notification data payload cannot be deserialized", exception);
		}
	}
}
