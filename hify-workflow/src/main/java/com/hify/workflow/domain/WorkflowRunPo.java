package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_workflow_run")
@EqualsAndHashCode(callSuper = false)
public class WorkflowRunPo extends BaseEntity {

    private Long workflowId;

    private Long workflowVersionId;

    private String status;

    private String input;

    private String output;

    private String error;

    private String currentNodeKey;

    private LocalDateTime timeoutAt;

    private String runMode;

    private String contextSnapshot;

    private Integer elapsedMs;

    private LocalDateTime finishedAt;
}
