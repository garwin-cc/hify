package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow")
@EqualsAndHashCode(callSuper = false)
public class WorkflowPo extends BaseEntity {

    private Long workspaceId;

    private Long projectId;

    private String name;

    private String description;

    private Integer enabled;

    private String startNodeKey;

    private Long templateId;

    private Long sourceTemplateVersionId;
}
