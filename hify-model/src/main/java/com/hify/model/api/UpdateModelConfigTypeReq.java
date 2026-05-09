package com.hify.model.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateModelConfigTypeReq {

    @NotBlank(message = "模型类型不能为空")
    private String modelType;
}
