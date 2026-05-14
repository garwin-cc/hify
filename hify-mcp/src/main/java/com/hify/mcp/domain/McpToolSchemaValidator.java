package com.hify.mcp.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class McpToolSchemaValidator {

    @SuppressWarnings("unchecked")
    public void validate(Map<String, Object> schema, Map<String, Object> arguments) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        Object rootType = schema.get("type");
        if (rootType != null && !"object".equals(String.valueOf(rootType))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数 schema 根类型仅支持 object");
        }
        Object required = schema.get("required");
        if (required instanceof List<?> requiredFields) {
            for (Object field : requiredFields) {
                String name = String.valueOf(field);
                if (!args.containsKey(name)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "工具参数缺少必填参数: " + name);
                }
            }
        }
        Map<String, Object> properties = schema.get("properties") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            for (String key : args.keySet()) {
                if (!properties.containsKey(key)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "工具参数包含不允许的参数: " + key);
                }
            }
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!args.containsKey(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                validateType(entry.getKey(), (Map<String, Object>) propertySchema, args.get(entry.getKey()));
            }
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!args.containsKey(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                validateEnum(entry.getKey(), (Map<String, Object>) propertySchema, args.get(entry.getKey()));
            }
        }
    }

    private void validateType(String name, Map<String, Object> schema, Object value) {
        Object type = schema.get("type");
        if (type != null && value != null) {
            String expected = String.valueOf(type);
            boolean valid = switch (expected) {
                case "string" -> value instanceof String;
                case "integer" -> value instanceof Integer || value instanceof Long;
                case "number" -> value instanceof Number || value instanceof BigDecimal;
                case "boolean" -> value instanceof Boolean;
                case "object" -> value instanceof Map<?, ?>;
                case "array" -> value instanceof List<?>;
                default -> true;
            };
            if (!valid) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工具参数类型不匹配: " + name);
            }
        }
    }

    private void validateEnum(String name, Map<String, Object> schema, Object value) {
        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> enums && !enums.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数不在枚举范围: " + name);
        }
    }
}
