package com.hify.model.api;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProviderReq {

    @Size(max = 100, message = "名称不超过 100 字符")
    private String name;

    private String type;

    private String baseUrl;

    /** 传入则更新密钥，留空不修改 */
    private String apiKey;

    /** 传入则更新启用状态：1=启用 0=禁用 */
    private Integer enabled;

    private Integer sortOrder;
}
