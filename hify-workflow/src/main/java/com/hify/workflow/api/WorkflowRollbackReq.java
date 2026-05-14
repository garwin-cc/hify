package com.hify.workflow.api;

import lombok.Data;

@Data
public class WorkflowRollbackReq {

    private Boolean publish;

    private String publishType;

    private String changeSummary;
}
