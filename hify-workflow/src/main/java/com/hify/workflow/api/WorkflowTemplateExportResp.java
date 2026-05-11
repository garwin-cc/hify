package com.hify.workflow.api;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class WorkflowTemplateExportResp {

    private String filename;

    private JsonNode templateJson;
}
