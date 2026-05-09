package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class ConditionNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        try {
            ConditionNodeConfig condition = requireConfig(config, ConditionNodeConfig.class);
            String expression = ctx.resolve(condition.expression());
            boolean result = evaluate(expression);
            ctx.set(node.nodeKey(), outputVariable(condition.outputVariable()), result);
        } catch (Exception e) {
            throw toExecuteException(node, e);
        }
    }

    @Override
    public String nodeType() {
        return "CONDITION";
    }

    private boolean evaluate(String expression) {
        String normalized = expression == null ? "" : expression.trim();
        int equalsIndex = normalized.indexOf("==");
        if (equalsIndex >= 0) {
            String left = normalize(normalized.substring(0, equalsIndex));
            String right = normalize(normalized.substring(equalsIndex + 2));
            return left.equals(right);
        }
        int notEqualsIndex = normalized.indexOf("!=");
        if (notEqualsIndex >= 0) {
            String left = normalize(normalized.substring(0, notEqualsIndex));
            String right = normalize(normalized.substring(notEqualsIndex + 2));
            return !left.equals(right);
        }
        normalized = normalize(normalized);
        if ("true".equalsIgnoreCase(normalized)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalized)) {
            return false;
        }
        return Boolean.parseBoolean(normalized);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        if ((result.startsWith("\"") && result.endsWith("\""))
                || (result.startsWith("'") && result.endsWith("'"))) {
            return result.substring(1, result.length() - 1);
        }
        return result;
    }
}
