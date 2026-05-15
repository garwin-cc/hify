package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

@Component
public class VariableAssignerNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    private static final Pattern VARIABLE_NAME_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        VariableAssignerConfig assignerConfig = requireConfig(config, VariableAssignerConfig.class);
        Map<String, String> assignments = assignerConfig.assignments();
        if (assignments == null || assignments.isEmpty()) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "VARIABLE_ASSIGNER 至少需要一个变量赋值");
        }
        assignments.forEach((variableName, template) -> {
            validateVariableName(variableName);
            ctx.set(node.nodeKey(), variableName, ctx.resolve(template));
        });
    }

    @Override
    public String nodeType() {
        return "VARIABLE_ASSIGNER";
    }

    private void validateVariableName(String variableName) {
        if (variableName == null || !VARIABLE_NAME_PATTERN.matcher(variableName).matches()) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "VARIABLE_ASSIGNER 变量名不合法: " + variableName);
        }
    }
}
