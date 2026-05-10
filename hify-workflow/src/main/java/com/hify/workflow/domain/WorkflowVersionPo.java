package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_version")
@EqualsAndHashCode(callSuper = false)
public class WorkflowVersionPo extends BaseEntity {

    private Long workflowId;

    private Integer versionNo;

    private String snapshotJson;

    private String changeSummary;
}
