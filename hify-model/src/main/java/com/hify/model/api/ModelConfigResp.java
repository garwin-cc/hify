package com.hify.model.api;

import lombok.Data;

import java.util.Map;

@Data
public class ModelConfigResp {
    private Long id;
    private Long providerId;
    private String providerType;
    private String name;
    private String modelId;
    private String modelType;
    private Integer contextSize;
    private Map<String, Object> extraParams;
    private Integer enabled;
    private Integer sortOrder;
}
