package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_workflow_review_task")
@EqualsAndHashCode(callSuper = false)
public class WorkflowReviewTaskPo extends BaseEntity {

    private Long workflowRunId;

    private String nodeKey;

    private String status;

    private String title;

    private String content;

    private String actionsJson;

    private Integer allowEdit;

    private String outputVariable;

    private Long assigneeUserId;

    private String assigneeUsername;

    private LocalDateTime dueAt;

    private String timeoutAction;

    private LocalDateTime notifiedAt;

    private LocalDateTime expiredAt;

    private String reviewAction;

    private String reviewComment;

    private String editedContent;

    private String reviewedBy;

    private LocalDateTime reviewedAt;
}
