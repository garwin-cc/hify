package com.hify.workflow.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowPublishResp {

    private Long id;

    private Long workflowId;

    private Long workflowVersionId;

    private String publishType;

    private String publishStatus;

    private String endpointKey;

    private String toolKey;

    private String displayName;

    private Integer grayPercent;

    private LocalDateTime publishedAt;
}
