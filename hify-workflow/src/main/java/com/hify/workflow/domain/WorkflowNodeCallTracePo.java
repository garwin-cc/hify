package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_workflow_node_call_trace")
@EqualsAndHashCode(callSuper = false)
public class WorkflowNodeCallTracePo extends BaseEntity {

    private Long workflowRunId;

    private Long workflowNodeRunId;

    private String nodeKey;

    private String nodeType;

    private String callType;

    private String target;

    private String requestSnapshot;

    private String responseSnapshot;

    private String status;

    private String errorMessage;

    private Integer durationMs;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}
