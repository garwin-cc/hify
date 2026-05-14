package com.hify.auth.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.auth.api.AuditLogRecord;
import com.hify.auth.infra.AuditLogMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogServiceImplTest {

    @Test
    void recordMasksSensitiveFieldsBeforePersisting() {
        AtomicReference<AuditLogPo> inserted = new AtomicReference<>();
        AuditLogServiceImpl service = new AuditLogServiceImpl(mapper(inserted), new ObjectMapper());

        AuditLogRecord record = AuditLogRecord.builder()
                .traceId("trace-1")
                .actorUserId(100L)
                .actorUsername("admin")
                .workspaceId(1L)
                .projectId(2L)
                .action("PROVIDER_UPDATE")
                .resourceType("PROVIDER")
                .resourceId(3L)
                .resourceName("OpenAI")
                .requestMethod("PUT")
                .requestPath("/api/v1/providers/3")
                .clientIp("127.0.0.1")
                .userAgent("JUnit")
                .success(true)
                .before(Map.of("apiKey", "sk-old", "name", "OpenAI"))
                .after(new LinkedHashMap<>(Map.of("apiKey", "sk-new", "baseUrl", "https://example.com")))
                .build();

        service.record(record);

        assertThat(inserted.get()).isNotNull();
        assertThat(inserted.get().getAction()).isEqualTo("PROVIDER_UPDATE");
        assertThat(inserted.get().getBeforeJson()).contains("\"apiKey\":\"******\"");
        assertThat(inserted.get().getBeforeJson()).doesNotContain("sk-old");
        assertThat(inserted.get().getAfterJson()).contains("\"apiKey\":\"******\"");
        assertThat(inserted.get().getAfterJson()).doesNotContain("sk-new");
        assertThat(inserted.get().getAfterJson()).contains("https://example.com");
    }

    private static AuditLogMapper mapper(AtomicReference<AuditLogPo> inserted) {
        return proxy(AuditLogMapper.class, method -> {
            if ("insert".equals(method)) {
                return args -> {
                    AuditLogPo po = (AuditLogPo) args[0];
                    po.setId(1000L);
                    inserted.set(po);
                    return 1;
                };
            }
            return null;
        });
    }

    private static <T> T proxy(Class<T> type, Function<String, Invocation> behavior) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Invocation invocation = behavior.apply(method.getName());
                    if (invocation != null) {
                        return invocation.invoke(args == null ? new Object[0] : args);
                    }
                    if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class)) {
                        return 0;
                    }
                    return null;
                }));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Object[] args);
    }
}
