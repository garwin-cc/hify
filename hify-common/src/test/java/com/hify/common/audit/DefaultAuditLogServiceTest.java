package com.hify.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultAuditLogServiceTest {

    @Test
    void masksSensitiveFieldsBeforePersisting() {
        AtomicReference<AuditLogPo> inserted = new AtomicReference<>();
        DefaultAuditLogService service = new DefaultAuditLogService(inserted::set, new ObjectMapper());

        Map<String, Object> after = new HashMap<>();
        after.put("apiKey", "sk-secret");
        after.put("name", "openai");

        service.record(AuditLogRecord.builder()
                .traceId("trace-1")
                .action("PROVIDER_UPDATE")
                .resourceType("PROVIDER")
                .after(after)
                .success(true)
                .build());

        assertThat(inserted.get().getAfterJson()).contains("\"apiKey\":\"******\"");
        assertThat(inserted.get().getAfterJson()).contains("\"name\":\"openai\"");
    }

    @Test
    void masksNestedSensitiveFieldsBeforePersisting() {
        AtomicReference<AuditLogPo> inserted = new AtomicReference<>();
        DefaultAuditLogService service = new DefaultAuditLogService(inserted::set, new ObjectMapper());

        Map<String, Object> after = new HashMap<>();
        after.put("authConfig", Map.of(
                "headers", Map.of("Authorization", "Bearer raw-token"),
                "clientSecret", "raw-secret"));
        after.put("tools", java.util.List.of(Map.of("apiKey", "tool-secret", "name", "search")));

        service.record(AuditLogRecord.builder()
                .traceId("trace-1")
                .action("MCP_UPDATE")
                .resourceType("MCP")
                .after(after)
                .success(true)
                .build());

        assertThat(inserted.get().getAfterJson()).doesNotContain("raw-token");
        assertThat(inserted.get().getAfterJson()).doesNotContain("raw-secret");
        assertThat(inserted.get().getAfterJson()).doesNotContain("tool-secret");
        assertThat(inserted.get().getAfterJson()).contains("\"authConfig\":\"******\"");
        assertThat(inserted.get().getAfterJson()).contains("\"name\":\"search\"");
    }
}
