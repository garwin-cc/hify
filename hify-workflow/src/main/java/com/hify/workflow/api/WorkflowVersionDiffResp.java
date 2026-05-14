package com.hify.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class WorkflowVersionDiffResp {

    private Long workflowId;

    private Integer leftVersionNo;

    private Integer rightVersionNo;

    private JsonNode summary;
}
