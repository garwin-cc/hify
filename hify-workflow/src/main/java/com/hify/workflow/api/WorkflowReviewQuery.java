package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowReviewQuery {

    private Long assigneeUserId;

    private String assigneeUsername;

    private String status;

    private Boolean expiredOnly;

    private LocalDateTime dueBefore;

    private int page = 1;

    private int size = 20;
}
