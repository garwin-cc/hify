package com.hify.mcp.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_tool_secret")
@EqualsAndHashCode(callSuper = false)
public class ToolSecretPo extends BaseEntity {

    private Long workspaceId;

    private Long projectId;

    private String name;

    private String secretType;

    private String keyPrefix;

    private String encryptedValue;

    private String status;
}
