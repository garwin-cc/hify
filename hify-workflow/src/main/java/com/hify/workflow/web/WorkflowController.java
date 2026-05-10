package com.hify.workflow.web;

import com.hify.common.web.PageResult;
import com.hify.common.web.Result;
import com.hify.workflow.api.CreateWorkflowReq;
import com.hify.workflow.api.UpdateWorkflowReq;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowListItemResp;
import com.hify.workflow.api.WorkflowQuery;
import com.hify.workflow.api.WorkflowRunReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.api.WorkflowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowService workflowService;

    @PostMapping
    public Result<WorkflowDetailResp> create(@Valid @RequestBody CreateWorkflowReq req) {
        return Result.ok(workflowService.create(req));
    }

    @GetMapping
    public PageResult<WorkflowListItemResp> list(WorkflowQuery query) {
        return workflowService.listPage(query);
    }

    @GetMapping("/{id}")
    public Result<WorkflowDetailResp> getDetail(@PathVariable Long id) {
        return Result.ok(workflowService.getDetail(id));
    }

    @PutMapping("/{id}")
    public Result<WorkflowDetailResp> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateWorkflowReq req) {
        return Result.ok(workflowService.update(id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        workflowService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/run")
    public Result<WorkflowRunResp> run(@PathVariable Long id,
                                       @Valid @RequestBody WorkflowRunReq req) {
        return Result.ok(workflowService.run(id, req));
    }

    @PostMapping("/{id}/runs")
    public Result<WorkflowRunResp> startAsyncRun(@PathVariable Long id,
                                                 @Valid @RequestBody WorkflowRunReq req) {
        return Result.ok(workflowService.startAsyncRun(id, req));
    }

    @GetMapping("/{id}/runs/latest")
    public Result<WorkflowRunResp> getLatestRun(@PathVariable Long id) {
        return Result.ok(workflowService.getLatestRun(id));
    }

}
