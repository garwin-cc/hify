package com.hify.model.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ModelDefaultPolicyReq {

    @NotBlank
    private String scopeType;

    private Long scopeId;

    @NotBlank
    private String modelType;

    private String providerType;

    @NotNull
    private Long modelConfigId;

    private Integer enabled;
}
