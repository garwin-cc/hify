package com.hify.auth.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProjectReq {

    private Long workspaceId;

    @NotBlank(message = "项目名称不能为空")
    private String name;

    @NotBlank(message = "项目编码不能为空")
    private String code;
}
