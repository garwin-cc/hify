package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class CodeTaskNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    private final CodeTaskRunnerRegistry runnerRegistry;

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        CodeTaskConfig codeConfig = requireConfig(config, CodeTaskConfig.class);
        if (!StringUtils.hasText(codeConfig.task())) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "CODE_TASK 缺少任务描述");
        }
        if (StringUtils.hasText(codeConfig.executor()) && !"MCP".equalsIgnoreCase(codeConfig.executor())) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "CODE_TASK 只允许通过 MCP Code Worker 执行");
        }
        long startedAt = System.currentTimeMillis();
        CodeTaskResult result = runnerRegistry.get(codeConfig.executor()).run(node, codeConfig, ctx);
        ctx.recordCall(new WorkflowCallTrace(
                "CODE_TASK",
                codeConfig.executor() == null ? "MCP" : codeConfig.executor(),
                java.util.Map.of("executor", codeConfig.executor() == null ? "MCP" : codeConfig.executor(),
                        "toolName", codeConfig.toolName() == null ? "" : codeConfig.toolName(),
                        "sandboxRequired", Boolean.TRUE.equals(codeConfig.sandboxRequired()),
                        "approvalRequired", Boolean.TRUE.equals(codeConfig.approvalRequired()),
                        "diffAuditRequired", Boolean.TRUE.equals(codeConfig.diffAuditRequired())),
                java.util.Map.of("status", result.status(),
                        "changedFiles", result.changedFiles(),
                        "hasDiff", StringUtils.hasText(result.diff())),
                result.error() == null || result.error().isBlank() ? "SUCCESS" : "FAILED",
                result.error(),
                elapsed(startedAt)));
        String outputVariable = StringUtils.hasText(codeConfig.outputVariable()) ? codeConfig.outputVariable() : "result";
        ctx.set(node.nodeKey(), outputVariable, result);
    }

    @Override
    public String nodeType() {
        return "CODE_TASK";
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }
}
