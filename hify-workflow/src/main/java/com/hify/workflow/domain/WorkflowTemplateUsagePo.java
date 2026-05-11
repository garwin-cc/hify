package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_template_usage")
@EqualsAndHashCode(callSuper = false)
public class WorkflowTemplateUsagePo extends BaseEntity {

    private Long templateId;

    private Long versionId;

    private Long workflowId;

    private String actionType;
}
