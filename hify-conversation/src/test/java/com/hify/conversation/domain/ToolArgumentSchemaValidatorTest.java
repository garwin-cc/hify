package com.hify.conversation.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ToolArgumentSchemaValidatorTest {

    private final ToolArgumentSchemaValidator validator = new ToolArgumentSchemaValidator();

    @Test
    void validateRejectsMissingRequiredArgument() {
        Map<String, Object> schema = Map.of("required", List.of("query"));

        String error = validator.validate(schema, Map.of());

        assertThat(error).isEqualTo("缺少必填参数: query");
    }

    @Test
    void validateRejectsTypeMismatch() {
        Map<String, Object> schema = Map.of("properties",
                Map.of("limit", Map.of("type", "integer")));

        String error = validator.validate(schema, Map.of("limit", "10"));

        assertThat(error).isEqualTo("参数类型不匹配: limit 需要 integer");
    }

    @Test
    void validateAcceptsSupportedSchemaSubset() {
        Map<String, Object> schema = Map.of(
                "required", List.of("query", "limit"),
                "properties", Map.of(
                        "query", Map.of("type", "string"),
                        "limit", Map.of("type", "integer")));

        String error = validator.validate(schema, Map.of("query", "hello", "limit", 3));

        assertThat(error).isNull();
    }
}
