package com.hify.mcp.domain;

import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpToolSchemaValidatorTest {

    private final McpToolSchemaValidator validator = new McpToolSchemaValidator();

    @Test
    void acceptsValidArguments() {
        validator.validate(schema(), Map.of("query", "hello", "topK", 3, "mode", "fast"));
    }

    @Test
    void rejectsMissingRequiredField() {
        assertThatThrownBy(() -> validator.validate(schema(), Map.of("topK", 3)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("缺少必填参数");
    }

    @Test
    void rejectsWrongTypeAndEnumValue() {
        assertThatThrownBy(() -> validator.validate(schema(), Map.of("query", 123, "mode", "slow")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("类型不匹配");
        assertThatThrownBy(() -> validator.validate(schema(), Map.of("query", "hello", "mode", "invalid")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不在枚举范围");
    }

    @Test
    void rejectsUnexpectedPropertyWhenAdditionalPropertiesFalse() {
        assertThatThrownBy(() -> validator.validate(schema(), Map.of("query", "hello", "extra", true)))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不允许的参数");
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "required", List.of("query"),
                "additionalProperties", false,
                "properties", Map.of(
                        "query", Map.of("type", "string"),
                        "topK", Map.of("type", "integer"),
                        "mode", Map.of("type", "string", "enum", List.of("fast", "safe"))
                )
        );
    }
}
