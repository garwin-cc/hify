package com.hify.model.api;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ModelDefaultPolicyResp {

    private Long id;
    private String scopeType;
    private Long scopeId;
    private String modelType;
    private String providerType;
    private Long modelConfigId;
    private Integer enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
