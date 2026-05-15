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

    @Test
    void acceptsNestedObjectArrayAndRangeConstraints() {
        validator.validate(nestedSchema(), Map.of(
                "query", "invoice-2026",
                "filter", Map.of("department", "finance", "tags", List.of("policy", "expense")),
                "limit", 20
        ));
    }

    @Test
    void rejectsNestedObjectAndArrayViolations() {
        assertThatThrownBy(() -> validator.validate(nestedSchema(), Map.of(
                "query", "invoice-2026",
                "filter", Map.of("tags", List.of("policy"))
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("filter.department");

        assertThatThrownBy(() -> validator.validate(nestedSchema(), Map.of(
                "query", "invoice-2026",
                "filter", Map.of("department", "finance", "tags", List.of("policy", 3))
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("filter.tags[1]");
    }

    @Test
    void rejectsStringPatternLengthAndNumericRangeViolations() {
        assertThatThrownBy(() -> validator.validate(nestedSchema(), Map.of(
                "query", "bad value",
                "filter", Map.of("department", "finance"),
                "limit", 20
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("格式不匹配");

        assertThatThrownBy(() -> validator.validate(nestedSchema(), Map.of(
                "query", "invoice-2026",
                "filter", Map.of("department", "finance"),
                "limit", 200
        )))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超过最大值");
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

    private Map<String, Object> nestedSchema() {
        return Map.of(
                "type", "object",
                "required", List.of("query", "filter"),
                "additionalProperties", false,
                "properties", Map.of(
                        "query", Map.of(
                                "type", "string",
                                "minLength", 3,
                                "maxLength", 30,
                                "pattern", "^[a-z0-9-]+$"
                        ),
                        "filter", Map.of(
                                "type", "object",
                                "required", List.of("department"),
                                "additionalProperties", false,
                                "properties", Map.of(
                                        "department", Map.of("type", "string"),
                                        "tags", Map.of(
                                                "type", "array",
                                                "items", Map.of("type", "string")
                                        )
                                )
                        ),
                        "limit", Map.of(
                                "type", "integer",
                                "minimum", 1,
                                "maximum", 50
                        )
                )
        );
    }
}
