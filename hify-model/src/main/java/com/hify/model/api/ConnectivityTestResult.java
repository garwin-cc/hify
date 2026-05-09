package com.hify.model.api;

import lombok.Getter;

import java.util.List;

@Getter
public class ConnectivityTestResult {

    private final boolean success;
    private final int     latencyMs;
    /** 从响应中解析到的模型数量；探活失败时为 null */
    private final Integer modelCount;
    /** 从模型列表响应中解析到的模型 ID，供测试成功后同步 t_model_config。 */
    private final List<String> modelIds;
    /** 失败原因；成功时为空串 */
    private final String  errorMessage;

    private ConnectivityTestResult(boolean success, int latencyMs,
                                   Integer modelCount, List<String> modelIds, String errorMessage) {
        this.success      = success;
        this.latencyMs    = latencyMs;
        this.modelCount   = modelCount;
        this.modelIds     = modelIds;
        this.errorMessage = errorMessage;
    }

    public static ConnectivityTestResult success(int latencyMs, int modelCount) {
        return new ConnectivityTestResult(true, latencyMs, modelCount, List.of(), "");
    }

    public static ConnectivityTestResult success(int latencyMs, List<String> modelIds) {
        return new ConnectivityTestResult(true, latencyMs, modelIds.size(), List.copyOf(modelIds), "");
    }

    public static ConnectivityTestResult failure(int latencyMs, String errorMessage) {
        return new ConnectivityTestResult(false, latencyMs, null, List.of(), errorMessage);
    }
}
