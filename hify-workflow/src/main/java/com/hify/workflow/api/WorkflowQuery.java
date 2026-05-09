package com.hify.workflow.api;

import lombok.Data;

@Data
public class WorkflowQuery {

    private String name;

    private Integer enabled;

    private int page = 1;

    private int size = 20;
}
