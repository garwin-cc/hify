package com.hify.workflow.api;

import lombok.Data;

@Data
public class WorkflowTemplateRequirementResp {

    private String key;

    private String type;

    private String label;

    private Boolean required;
}
