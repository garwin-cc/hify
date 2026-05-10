package com.hify.workflow.web;

import com.hify.common.web.Result;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow-runs")
@RequiredArgsConstructor
public class WorkflowRunController {

    private final WorkflowService workflowService;

    @GetMapping("/{runId}")
    public Result<WorkflowRunResp> getRunDetail(@PathVariable Long runId) {
        return Result.ok(workflowService.getRunDetail(runId));
    }
}
