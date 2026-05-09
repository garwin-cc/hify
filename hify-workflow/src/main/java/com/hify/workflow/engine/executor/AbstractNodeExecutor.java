package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;

abstract class AbstractNodeExecutor {

    protected <T extends NodeConfigDef> T requireConfig(NodeConfigDef config, Class<T> type) {
        if (!type.isInstance(config)) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID,
                    "节点配置类型不匹配，expected=" + type.getSimpleName());
        }
        return type.cast(config);
    }

    protected BizException toExecuteException(WorkflowNode node, Exception e) {
        if (e instanceof BizException bizException) {
            return bizException;
        }
        return new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                "工作流节点执行失败: nodeKey=" + node.nodeKey() + " type=" + node.nodeType(), e);
    }

    protected String outputVariable(String value) {
        if (value == null || value.isBlank()) {
            return "output";
        }
        return value;
    }
}
