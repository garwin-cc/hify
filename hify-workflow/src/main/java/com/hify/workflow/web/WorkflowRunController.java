package com.hify.workflow.web;

import com.hify.common.web.Result;
import com.hify.workflow.api.SubmitWorkflowReviewReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import com.hify.workflow.api.WorkflowReviewTaskResp;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/workflow-runs")
@RequiredArgsConstructor
public class WorkflowRunController {

    private final WorkflowService workflowService;

    @GetMapping("/{runId}")
    public Result<WorkflowRunResp> getRunDetail(@PathVariable Long runId) {
        return Result.ok(workflowService.getRunDetail(runId));
    }

    @GetMapping("/{runId}/events")
    public SseEmitter streamEvents(@PathVariable Long runId,
                                   @RequestParam(required = false) Integer after,
                                   @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return workflowService.streamRunEvents(runId, resolveAfter(after, lastEventId));
    }

    @GetMapping("/{runId}/review")
    public Result<WorkflowReviewTaskResp> getReviewTask(@PathVariable Long runId) {
        return Result.ok(workflowService.getReviewTask(runId));
    }

    @PostMapping("/{runId}/review")
    public Result<WorkflowRunResp> submitReview(@PathVariable Long runId,
                                                @Valid @RequestBody SubmitWorkflowReviewReq req) {
        return Result.ok(workflowService.submitReview(runId, req));
    }

    private Integer resolveAfter(Integer after, String lastEventId) {
        if (after != null) {
            return after;
        }
        if (lastEventId == null || lastEventId.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(lastEventId);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
