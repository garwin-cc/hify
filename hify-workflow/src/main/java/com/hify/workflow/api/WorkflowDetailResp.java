package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class WorkflowDetailResp {

    private Long id;

    private String name;

    private String description;

    private Integer enabled;

    private String startNodeKey;

    private List<WorkflowNodeDto> nodes;

    private List<WorkflowEdgeDto> edges;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
