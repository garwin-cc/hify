package com.hify.workflow.api;

import lombok.Data;

import java.util.Map;

@Data
public class WorkflowNodeDebugReq {

    private String userMessage;

    private Map<String, Object> variables;
}
