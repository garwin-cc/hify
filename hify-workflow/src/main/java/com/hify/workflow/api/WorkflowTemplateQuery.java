package com.hify.workflow.api;

import lombok.Data;

@Data
public class WorkflowTemplateQuery {

    private int page = 1;

    private int size = 20;

    private String name;

    private String category;
}
