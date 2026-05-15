package com.hify.mcp.domain;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
public class McpToolSchemaValidator {

    @SuppressWarnings("unchecked")
    public void validate(Map<String, Object> schema, Map<String, Object> arguments) {
        if (schema == null || schema.isEmpty()) {
            return;
        }
        Map<String, Object> args = arguments == null ? Map.of() : arguments;
        if (schema.get("type") != null && !"object".equals(String.valueOf(schema.get("type")))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数 schema 根类型仅支持 object");
        }
        validateObject("$", schema, args);
    }

    @SuppressWarnings("unchecked")
    private void validateObject(String path, Map<String, Object> schema, Map<String, Object> args) {
        Object required = schema.get("required");
        if (required instanceof List<?> requiredFields) {
            for (Object field : requiredFields) {
                String name = String.valueOf(field);
                if (!args.containsKey(name)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "工具参数缺少必填参数: " + childPath(path, name));
                }
            }
        }
        Map<String, Object> properties = schema.get("properties") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();
        if (Boolean.FALSE.equals(schema.get("additionalProperties"))) {
            for (String key : args.keySet()) {
                if (!properties.containsKey(key)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "工具参数包含不允许的参数: " + childPath(path, key));
                }
            }
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!args.containsKey(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                validateType(childPath(path, entry.getKey()), (Map<String, Object>) propertySchema, args.get(entry.getKey()));
            }
        }
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!args.containsKey(entry.getKey())) {
                continue;
            }
            if (entry.getValue() instanceof Map<?, ?> propertySchema) {
                validateValue(childPath(path, entry.getKey()), (Map<String, Object>) propertySchema,
                        args.get(entry.getKey()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void validateValue(String path, Map<String, Object> schema, Object value) {
        validateType(path, schema, value);
        validateEnum(path, schema, value);
        if (value == null) {
            return;
        }
        Object type = schema.get("type");
        String expected = type == null ? "" : String.valueOf(type);
        if ("object".equals(expected) && value instanceof Map<?, ?> map) {
            validateObject(path, schema, (Map<String, Object>) map);
            return;
        }
        if ("array".equals(expected) && value instanceof List<?> list) {
            Object items = schema.get("items");
            if (items instanceof Map<?, ?> itemSchema) {
                for (int i = 0; i < list.size(); i++) {
                    validateValue(path + "[" + i + "]", (Map<String, Object>) itemSchema, list.get(i));
                }
            }
            return;
        }
        if ("string".equals(expected) && value instanceof String text) {
            validateString(path, schema, text);
            return;
        }
        if (("integer".equals(expected) || "number".equals(expected)) && value instanceof Number number) {
            validateNumber(path, schema, number);
        }
    }

    private void validateType(String path, Map<String, Object> schema, Object value) {
        Object type = schema.get("type");
        if (type == null || value == null) {
            return;
        }
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
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数类型不匹配: " + path);
        }
    }

    private void validateEnum(String path, Map<String, Object> schema, Object value) {
        Object enumValues = schema.get("enum");
        if (enumValues instanceof List<?> enums && !enums.contains(value)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数不在枚举范围: " + path);
        }
    }

    private void validateString(String path, Map<String, Object> schema, String value) {
        Integer minLength = toInteger(schema.get("minLength"));
        if (minLength != null && value.length() < minLength) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数长度小于最小值: " + path);
        }
        Integer maxLength = toInteger(schema.get("maxLength"));
        if (maxLength != null && value.length() > maxLength) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数长度超过最大值: " + path);
        }
        Object pattern = schema.get("pattern");
        if (pattern instanceof String regex && !regex.isBlank()) {
            try {
                if (!Pattern.compile(regex).matcher(value).matches()) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "工具参数格式不匹配: " + path);
                }
            } catch (PatternSyntaxException e) {
                throw new BizException(ErrorCode.PARAM_ERROR, "工具参数 schema pattern 不合法: " + path);
            }
        }
    }

    private void validateNumber(String path, Map<String, Object> schema, Number value) {
        BigDecimal actual = toBigDecimal(value);
        BigDecimal minimum = toBigDecimal(schema.get("minimum"));
        if (minimum != null && actual.compareTo(minimum) < 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数小于最小值: " + path);
        }
        BigDecimal maximum = toBigDecimal(schema.get("maximum"));
        if (maximum != null && actual.compareTo(maximum) > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工具参数超过最大值: " + path);
        }
    }

    private static String childPath(String path, String child) {
        return "$".equals(path) ? child : path + "." + child;
    }

    private static Integer toInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
