package com.hify.conversation.domain;

import java.util.List;
import java.util.Map;

public class ToolArgumentSchemaValidator {

    public String validate(Map<String, Object> schema, Map<String, Object> arguments) {
        if (schema == null || schema.isEmpty()) {
            return null;
        }
        Object required = schema.get("required");
        if (required instanceof List<?> requiredList) {
            for (Object item : requiredList) {
                String key = String.valueOf(item);
                if (!arguments.containsKey(key) || arguments.get(key) == null) {
                    return "缺少必填参数: " + key;
                }
            }
        }
        Object properties = schema.get("properties");
        if (!(properties instanceof Map<?, ?> propertyMap)) {
            return null;
        }
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Object property = propertyMap.get(entry.getKey());
            if (!(property instanceof Map<?, ?> propertySchema)) {
                continue;
            }
            Object type = propertySchema.get("type");
            if (type == null || matchesType(String.valueOf(type), entry.getValue())) {
                continue;
            }
            return "参数类型不匹配: " + entry.getKey() + " 需要 " + type;
        }
        return null;
    }

    private boolean matchesType(String type, Object value) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "object" -> value instanceof Map<?, ?>;
            case "array" -> value instanceof List<?>;
            default -> true;
        };
    }
}
