package com.hify.common.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultAuditLogService implements AuditLogService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "apikey", "api_key", "password", "token", "authorization", "secret", "credential", "authconfig");
    private static final int MAX_JSON_LENGTH = 16_000;

    private final AuditLogWriter auditLogWriter;
    private final ObjectMapper objectMapper;

    @Override
    public void record(AuditLogRecord record) {
        if (record == null) {
            return;
        }
        try {
            AuditContext.Actor actor = AuditContext.currentActor();
            AuditLogPo po = new AuditLogPo();
            po.setTraceId(valueOrEmpty(record.getTraceId()));
            po.setActorUserId(record.getActorUserId() != null ? record.getActorUserId()
                    : actor == null ? null : actor.getUserId());
            po.setActorUsername(valueOrEmpty(record.getActorUsername() != null ? record.getActorUsername()
                    : actor == null ? "" : actor.getUsername()));
            po.setWorkspaceId(record.getWorkspaceId());
            po.setProjectId(record.getProjectId());
            po.setAction(valueOrEmpty(record.getAction()));
            po.setResourceType(valueOrEmpty(record.getResourceType()));
            po.setResourceId(record.getResourceId());
            po.setResourceName(valueOrEmpty(record.getResourceName()));
            po.setRequestMethod(valueOrEmpty(record.getRequestMethod()));
            po.setRequestPath(valueOrEmpty(record.getRequestPath()));
            po.setClientIp(valueOrEmpty(record.getClientIp()));
            po.setUserAgent(valueOrEmpty(record.getUserAgent()));
            po.setSuccess(record.isSuccess() ? 1 : 0);
            po.setErrorCode(valueOrEmpty(record.getErrorCode()));
            po.setErrorMessage(valueOrEmpty(record.getErrorMessage()));
            po.setBeforeJson(toJson(record.getBefore()));
            po.setAfterJson(toJson(record.getAfter()));
            auditLogWriter.insert(po);
        } catch (Exception e) {
            log.warn("audit log record failed action={} resourceType={} resourceId={} message={}",
                    record.getAction(), record.getResourceType(), record.getResourceId(), e.getMessage());
        }
    }

    private String toJson(Map<String, Object> value) throws JsonProcessingException {
        String json = objectMapper.writeValueAsString(sanitizeMap(value));
        if (json.length() <= MAX_JSON_LENGTH) {
            return json;
        }
        Map<String, Object> truncated = new LinkedHashMap<>();
        truncated.put("truncated", true);
        truncated.put("length", json.length());
        truncated.put("prefix", json.substring(0, MAX_JSON_LENGTH));
        return objectMapper.writeValueAsString(truncated);
    }

    private Map<String, Object> sanitizeMap(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(key, sensitive(key) ? "******" : sanitizeValue(item)));
        return result;
    }

    private Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, item) -> nested.put(String.valueOf(key), item));
            return sanitizeMap(nested);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::sanitizeValue).toList();
        }
        return value;
    }

    private boolean sensitive(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.toLowerCase(Locale.ROOT);
        return SENSITIVE_KEYS.stream().anyMatch(normalized::contains);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
