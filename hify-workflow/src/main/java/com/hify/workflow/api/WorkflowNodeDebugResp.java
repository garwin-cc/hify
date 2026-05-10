package com.hify.workflow.api;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowNodeDebugResp {

    private String status;

    private Map<String, Object> outputs;

    private String error;

    private Integer elapsedMs;
}
