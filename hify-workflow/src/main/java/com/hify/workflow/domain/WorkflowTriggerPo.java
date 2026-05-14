package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_workflow_trigger")
@EqualsAndHashCode(callSuper = false)
public class WorkflowTriggerPo extends BaseEntity {

    private Long workflowId;

    private Long workflowVersionId;

    private String triggerType;

    private String triggerKey;

    private String cronExpression;

    private Integer enabled;

    private LocalDateTime nextFireAt;

    private LocalDateTime lastFireAt;

    private Long lastRunId;
}
