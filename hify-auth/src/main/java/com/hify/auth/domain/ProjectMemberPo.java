package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_project_member")
@EqualsAndHashCode(callSuper = false)
public class ProjectMemberPo extends BaseEntity {

    private Long workspaceId;

    private Long projectId;

    private Long userId;

    private String role;

    private String status;
}
