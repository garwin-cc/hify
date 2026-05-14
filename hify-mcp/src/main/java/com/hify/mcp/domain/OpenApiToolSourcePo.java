package com.hify.mcp.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@TableName(value = "t_openapi_tool_source", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class OpenApiToolSourcePo extends BaseEntity {

    private Long workspaceId;

    private Long projectId;

    private String name;

    private String description;

    private String baseUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> specJson;

    private Long secretId;

    private Integer enabled;

    private String status;
}
