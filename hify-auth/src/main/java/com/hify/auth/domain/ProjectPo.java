package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_project")
@EqualsAndHashCode(callSuper = false)
public class ProjectPo extends BaseEntity {

    private Long workspaceId;

    private String name;

    private String code;

    private String status;
}
