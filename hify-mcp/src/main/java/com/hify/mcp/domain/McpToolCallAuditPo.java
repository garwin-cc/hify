package com.hify.mcp.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@TableName(value = "t_mcp_tool_call_audit", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class McpToolCallAuditPo extends BaseEntity {

    private String sourceType;

    private String traceId;

    private Long workspaceId;

    private Long projectId;

    private Long agentId;

    private Long appId;

    private Long apiKeyId;

    private Long userId;

    private Long conversationSessionId;

    private Long conversationMessageId;

    private Long workflowRunId;

    private Long workflowId;

    private String workflowNodeKey;

    private Long mcpServerId;

    private String toolName;

    private String status;

    private Integer retryCount;

    private Integer timeoutMs;

    private String sourceId;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> argumentKeys;

    private String argumentSummary;

    private Integer success;

    private Long elapsedMs;

    private String resultSummary;

    private String errorSummary;
}
