package com.hify.mcp.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@TableName(value = "t_openapi_tool", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class OpenApiToolPo extends BaseEntity {

    private Long sourceId;

    private String name;

    private String description;

    private String httpMethod;

    private String path;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputSchema;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> responseSchema;

    private Integer enabled;
}
