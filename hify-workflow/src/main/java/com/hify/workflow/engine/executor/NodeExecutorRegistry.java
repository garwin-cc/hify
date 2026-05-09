package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executorMap;

    public NodeExecutorRegistry(List<NodeExecutor> executors) {
        this.executorMap = executors.stream()
                .collect(Collectors.toMap(executor -> normalize(executor.nodeType()), executor -> executor));
    }

    public NodeExecutor get(String type) {
        NodeExecutor executor = executorMap.get(normalize(type));
        if (executor == null) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "不支持的节点执行器: " + type);
        }
        return executor;
    }

    private static String normalize(String type) {
        return type == null ? "" : type.toUpperCase(Locale.ROOT);
    }
}
