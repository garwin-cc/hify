package com.hify.demo.web.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateDemoItemReq {

    @NotBlank(message = "name 不能为空")
    private String name;

    @NotNull(message = "status 不能为空")
    private Integer status;
}
