package com.hify.workflow.domain;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class WorkflowSnapshotSanitizer {

    private static final String MASK = "******";
    private static final List<String> SENSITIVE_KEYS = List.of(
            "authorization", "apikey", "api_key", "token", "secret", "password", "cookie", "set-cookie");

    public Map<String, Object> sanitize(Map<String, Object> snapshot) {
        if (snapshot == null || snapshot.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        snapshot.forEach((key, value) -> sanitized.put(key, sanitizeValue(key, value)));
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private Object sanitizeValue(String key, Object value) {
        if (isSensitiveKey(key)) {
            return MASK;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) ->
                    sanitized.put(String.valueOf(nestedKey), sanitizeValue(String.valueOf(nestedKey), nestedValue)));
            return sanitized;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> sanitizeValue(key, item)).toList();
        }
        return value;
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.toLowerCase(Locale.ROOT).replace("-", "_");
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }
}
