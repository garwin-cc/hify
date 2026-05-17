package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_workflow_node_run")
@EqualsAndHashCode(callSuper = false)
public class WorkflowNodeRunPo extends BaseEntity {

    private Long workflowRunId;

    private String nodeKey;

    private String nodeType;

    private String status;

    private Integer attemptNo;

    private Integer maxAttempts;

    private Integer timeoutSeconds;

    private String failureStrategy;

    private Integer iterationIndex;

    private String inputSnapshot;

    private LocalDateTime startedAt;

    private String outputs;

    private String error;

    private Integer elapsedMs;

    private LocalDateTime finishedAt;
}
