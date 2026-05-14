package com.hify.workflow.api;

import lombok.Data;

@Data
public class WorkflowVariableResp {

    private String nodeKey;

    private String nodeType;

    private String variable;

    private String expression;

    private String label;
}
