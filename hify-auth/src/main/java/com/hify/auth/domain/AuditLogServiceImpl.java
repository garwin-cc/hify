package com.hify.auth.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.auth.api.AuditLogRecord;
import com.hify.auth.api.AuditLogService;
import com.hify.auth.infra.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "apikey", "api_key", "password", "token", "authorization", "secret", "credential");

    private final AuditLogMapper auditLogMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void record(AuditLogRecord record) {
        if (record == null) {
            return;
        }
        try {
            AuditLogPo po = new AuditLogPo();
            po.setTraceId(valueOrEmpty(record.getTraceId()));
            po.setActorUserId(record.getActorUserId());
            po.setActorUsername(valueOrEmpty(record.getActorUsername()));
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
            auditLogMapper.insert(po);
        } catch (Exception e) {
            log.warn("audit log record failed action={} resourceType={} resourceId={} message={}",
                    record.getAction(), record.getResourceType(), record.getResourceId(), e.getMessage());
        }
    }

    private String toJson(Map<String, Object> value) throws JsonProcessingException {
        Map<String, Object> sanitized = sanitize(value);
        return objectMapper.writeValueAsString(sanitized);
    }

    private Map<String, Object> sanitize(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        value.forEach((key, item) -> result.put(key, sensitive(key) ? "******" : item));
        return result;
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
