package com.hify.mcp.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_mcp_server")
@EqualsAndHashCode(callSuper = false)
public class McpServerPo extends BaseEntity {
    private String name;
    private String description;
    private String endpoint;
    private String authType;
    private String authConfig;
    private Integer enabled;
}
