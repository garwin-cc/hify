package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowRunReq {

    @NotBlank(message = "用户输入不能为空")
    private String userMessage;
}
