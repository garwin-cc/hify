package com.hify.mcp.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateToolSecretReq {

    private Long workspaceId;
    private Long projectId;

    @NotBlank(message = "Secret 名称不能为空")
    private String name;

    @NotBlank(message = "Secret 类型不能为空")
    private String secretType;

    @NotBlank(message = "Secret 值不能为空")
    private String secretValue;
}
