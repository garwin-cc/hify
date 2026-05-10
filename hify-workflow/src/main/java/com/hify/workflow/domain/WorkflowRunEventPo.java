package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_run_event")
@EqualsAndHashCode(callSuper = false)
public class WorkflowRunEventPo extends BaseEntity {

    private Long workflowRunId;

    private Integer eventSeq;

    private String eventType;

    private String nodeKey;

    private String status;

    private String payload;
}
