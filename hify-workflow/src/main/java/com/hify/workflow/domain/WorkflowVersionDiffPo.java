package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_version_diff")
@EqualsAndHashCode(callSuper = false)
public class WorkflowVersionDiffPo extends BaseEntity {

    private Long workflowId;

    private Long leftVersionId;

    private Long rightVersionId;

    private String summaryJson;
}
