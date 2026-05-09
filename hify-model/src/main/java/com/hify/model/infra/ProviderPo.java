package com.hify.model.infra;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@TableName(value = "t_provider", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class ProviderPo extends BaseEntity {

    private String name;

    /** OPENAI / ANTHROPIC / DEEPSEEK / ALIBABA / OLLAMA / OPENAI_COMPATIBLE */
    private String type;

    private String baseUrl;

    /**
     * 鉴权配置，结构随 type 不同而不同，由对应的 LlmAdapter 解析。
     * OPENAI/ANTHROPIC/DEEPSEEK/ALIBABA：{"apiKey": "sk-xxx"}
     * OLLAMA：{} 或 null
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> authConfig;

    private Integer enabled;

    private Integer sortOrder;
}
