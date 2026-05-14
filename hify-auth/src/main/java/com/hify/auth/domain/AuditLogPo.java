package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_audit_log")
@EqualsAndHashCode(callSuper = false)
public class AuditLogPo extends BaseEntity {

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
}
