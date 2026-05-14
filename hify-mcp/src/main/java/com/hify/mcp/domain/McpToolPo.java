package com.hify.mcp.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@TableName(value = "t_mcp_tool", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class McpToolPo extends BaseEntity {

    private Long mcpServerId;

    private String toolType;

    private Long openapiToolId;

    private String name;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSchema;

    private Integer dangerous;

    private String permissionLevel;

    private Integer schemaValidationEnabled;

    private Integer timeoutMs;

    private Integer retryTimes;

    private String fallbackStrategy;
}
