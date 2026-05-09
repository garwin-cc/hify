package com.hify.model.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateProviderReq {

    @NotBlank(message = "名称不能为空")
    @Size(max = 100, message = "名称不超过 100 字符")
    private String name;

    @NotBlank(message = "提供商类型不能为空")
    private String type;

    private String baseUrl;

    /** API 密钥，Ollama 等本地部署可留空 */
    private String apiKey;

    private Integer sortOrder;
}
