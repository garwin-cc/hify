package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SubmitWorkflowReviewReq {

    @NotBlank(message = "评审动作不能为空")
    private String action;

    private String comment;

    private String editedContent;
}
