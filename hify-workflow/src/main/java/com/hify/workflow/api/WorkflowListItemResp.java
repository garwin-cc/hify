package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowListItemResp {

    private Long id;

    private String name;

    private String description;

    private Integer enabled;

    private String startNodeKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
