package com.hify.model.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateModelConfigReq {

    @NotNull(message = "供应商不能为空")
    private Long providerId;

    @NotBlank(message = "模型名称不能为空")
    private String name;

    @NotBlank(message = "模型 ID 不能为空")
    private String modelId;

    @NotBlank(message = "模型类型不能为空")
    private String modelType;

    private Integer contextSize;
}
