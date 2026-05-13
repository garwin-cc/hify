package com.hify.workflow.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowSnapshotSanitizerTest {

    @Test
    void masksSensitiveValuesInNestedSnapshots() {
        WorkflowSnapshotSanitizer sanitizer = new WorkflowSnapshotSanitizer();

        Map<String, Object> sanitized = sanitizer.sanitize(Map.of(
                "headers", Map.of("Authorization", "Bearer token", "Accept", "application/json"),
                "apiKey", "secret-key",
                "body", Map.of("message", "hello")));

        Map<?, ?> headers = (Map<?, ?>) sanitized.get("headers");
        Map<?, ?> body = (Map<?, ?>) sanitized.get("body");
        assertThat(headers.get("Authorization")).isEqualTo("******");
        assertThat(headers.get("Accept")).isEqualTo("application/json");
        assertThat(sanitized).containsEntry("apiKey", "******");
        assertThat(body.get("message")).isEqualTo("hello");
    }
}
