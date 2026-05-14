package com.hify.auth.api;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class AuditLogRecord {

    private String traceId;

    private Long actorUserId;

    private String actorUsername;

    private Long workspaceId;

    private Long projectId;

    private String action;

    private String resourceType;

    private Long resourceId;

    private String resourceName;

    private String requestMethod;

    private String requestPath;

    private String clientIp;

    private String userAgent;

    private boolean success;

    private String errorCode;

    private String errorMessage;

    private Map<String, Object> before;

    private Map<String, Object> after;
}
