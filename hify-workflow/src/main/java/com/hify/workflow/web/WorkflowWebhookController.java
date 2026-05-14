package com.hify.workflow.web;

import com.hify.common.web.Result;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-webhooks")
@RequiredArgsConstructor
public class WorkflowWebhookController {

    private final WorkflowService workflowService;

    @PostMapping("/{triggerKey}")
    public Result<WorkflowRunResp> trigger(@PathVariable String triggerKey,
                                           @Valid @RequestBody WorkflowRunReq req) {
        return Result.ok(workflowService.triggerWebhook(triggerKey, req));
    }
}
