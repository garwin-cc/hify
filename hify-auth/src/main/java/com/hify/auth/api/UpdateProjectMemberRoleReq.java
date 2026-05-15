package com.hify.auth.api;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateProjectMemberRoleReq {

    @NotNull(message = "项目角色不能为空")
    private ProjectRole role;
}
