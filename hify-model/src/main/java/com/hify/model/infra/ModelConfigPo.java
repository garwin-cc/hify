package com.hify.model.infra;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@TableName(value = "t_model_config", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class ModelConfigPo extends BaseEntity {

    private Long providerId;

    /** 界面显示名称，如「GPT-4o」 */
    private String name;

    /** 调用 API 时传入的模型标识符，如 gpt-4o、claude-3-7-sonnet-20250219 */
    private String modelId;

    /** 模型用途：CHAT / EMBEDDING */
    private String modelType;

    /** 上下文窗口大小（token 数） */
    private Integer contextSize;

    /**
     * 模型级别扩展参数，透传给 API 请求，覆盖 Provider 默认值。
     * 例：{"temperature": 0.2, "top_p": 0.9}
     */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extraParams;

    private Integer enabled;

    private Integer sortOrder;
}
