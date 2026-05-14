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
}
