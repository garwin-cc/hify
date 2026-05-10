package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class CodeTaskRunnerRegistry {

    private final Map<String, CodeTaskRunner> runnerMap;

    public CodeTaskRunnerRegistry(List<CodeTaskRunner> runners) {
        this.runnerMap = runners.stream()
                .collect(Collectors.toMap(runner -> normalize(runner.executorType()), runner -> runner));
    }

    public CodeTaskRunner get(String executorType) {
        CodeTaskRunner runner = runnerMap.get(normalize(executorType == null ? "MCP" : executorType));
        if (runner == null) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "不支持的 CODE_TASK 执行器: " + executorType);
        }
        return runner;
    }

    private static String normalize(String value) {
        return value.toUpperCase(Locale.ROOT);
    }
}
