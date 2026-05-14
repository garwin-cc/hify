package com.hify.common.audit;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_audit_log")
public class AuditLogPo {

    private Long id;
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
    private Integer success;
    private String errorCode;
    private String errorMessage;
    private String beforeJson;
    private String afterJson;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deleted;
}
