package com.hify.auth.api;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AddProjectMemberReq {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "项目角色不能为空")
    private ProjectRole role;
}
