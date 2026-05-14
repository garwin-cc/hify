package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTriggerResp {

    private Long id;

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
