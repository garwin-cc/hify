package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

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
        List<String> orParts = splitLogical(normalized, "OR");
        if (orParts.size() > 1) {
            return orParts.stream().anyMatch(this::evaluateAnd);
        }
        return evaluateAnd(normalized);
    }

    private boolean evaluateAnd(String expression) {
        List<String> andParts = splitLogical(expression, "AND");
        if (andParts.size() > 1) {
            return andParts.stream().allMatch(this::evaluateSingle);
        }
        return evaluateSingle(expression);
    }

    private boolean evaluateSingle(String expression) {
        String normalized = expression == null ? "" : expression.trim();
        for (String operator : List.of(">=", "<=", "==", "!=", ">", "<")) {
            int index = indexOfSymbolOperator(normalized, operator);
            if (index >= 0) {
                String left = normalize(normalized.substring(0, index));
                String right = normalize(normalized.substring(index + operator.length()));
                return compare(left, right, operator);
            }
        }
        for (String operator : List.of("contains", "startsWith", "endsWith", "isEmpty", "isNotEmpty")) {
            SplitExpression split = splitWordOperator(normalized, operator);
            if (split != null) {
                String left = normalize(split.left());
                String right = normalize(split.right());
                return compare(left, right, operator);
            }
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

    private boolean compare(String left, String right, String operator) {
        return switch (operator) {
            case "==" -> left.equals(right);
            case "!=" -> !left.equals(right);
            case ">" -> number(left).compareTo(number(right)) > 0;
            case "<" -> number(left).compareTo(number(right)) < 0;
            case ">=" -> number(left).compareTo(number(right)) >= 0;
            case "<=" -> number(left).compareTo(number(right)) <= 0;
            case "contains" -> left.contains(right);
            case "startsWith" -> left.startsWith(right);
            case "endsWith" -> left.endsWith(right);
            case "isEmpty" -> left.isEmpty();
            case "isNotEmpty" -> !left.isEmpty();
            default -> false;
        };
    }

    private BigDecimal number(String value) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "条件表达式数值比较参数不是数字: " + value, e);
        }
    }

    private List<String> splitLogical(String expression, String operator) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        char quote = 0;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (isWordOperatorAt(expression, i, operator)) {
                parts.add(expression.substring(start, i).trim());
                i += operator.length() - 1;
                start = i + 1;
            }
        }
        if (parts.isEmpty()) {
            return List.of(expression);
        }
        parts.add(expression.substring(start).trim());
        return parts;
    }

    private int indexOfSymbolOperator(String expression, String operator) {
        char quote = 0;
        for (int i = 0; i <= expression.length() - operator.length(); i++) {
            char current = expression.charAt(i);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (expression.startsWith(operator, i)) {
                return i;
            }
        }
        return -1;
    }

    private SplitExpression splitWordOperator(String expression, String operator) {
        char quote = 0;
        for (int i = 0; i <= expression.length() - operator.length(); i++) {
            char current = expression.charAt(i);
            if (quote != 0) {
                if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
                continue;
            }
            if (isWordOperatorAt(expression, i, operator)) {
                return new SplitExpression(
                        expression.substring(0, i).trim(),
                        expression.substring(i + operator.length()).trim());
            }
        }
        return null;
    }

    private boolean isWordOperatorAt(String expression, int index, String operator) {
        if (index + operator.length() > expression.length()) {
            return false;
        }
        String candidate = expression.substring(index, index + operator.length());
        if (!candidate.equalsIgnoreCase(operator)) {
            return false;
        }
        boolean leftBoundary = index == 0 || !isWordChar(expression.charAt(index - 1));
        int rightIndex = index + operator.length();
        boolean rightBoundary = rightIndex >= expression.length() || !isWordChar(expression.charAt(rightIndex));
        return leftBoundary && rightBoundary;
    }

    private boolean isWordChar(char value) {
        return Character.isLetterOrDigit(value) || value == '_';
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

    private record SplitExpression(String left, String right) {
    }
}
