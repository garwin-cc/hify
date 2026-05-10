package com.hify.workflow.api;

import com.hify.common.web.PageResult;

/** 工作流服务，跨模块调用的统一入口。 */
public interface WorkflowService {

    WorkflowDetailResp create(CreateWorkflowReq req);

    PageResult<WorkflowListItemResp> listPage(WorkflowQuery query);

    WorkflowDetailResp getDetail(Long id);

    WorkflowDetailResp update(Long id, UpdateWorkflowReq req);

    void delete(Long id);

    WorkflowRunResp run(Long id, WorkflowRunReq req);

    WorkflowRunResp startAsyncRun(Long id, WorkflowRunReq req);

    WorkflowRunResp getRunDetail(Long runId);

    WorkflowRunResp getLatestRun(Long id);
}
