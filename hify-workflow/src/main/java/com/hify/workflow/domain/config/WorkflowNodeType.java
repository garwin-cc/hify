package com.hify.workflow.domain.config;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;

import java.util.Locale;

public enum WorkflowNodeType {
    START,
    LLM,
    CONDITION,
    API_CALL,
    KNOWLEDGE,
    TOOL,
    REPLY,
    END;

    public static WorkflowNodeType parse(String value) {
        try {
            return WorkflowNodeType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "不支持的节点类型: " + value);
        }
    }
}
