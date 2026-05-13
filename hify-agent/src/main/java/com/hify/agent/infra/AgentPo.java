package com.hify.agent.infra;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@TableName(value = "t_agent", autoResultMap = true)
@EqualsAndHashCode(callSuper = false)
public class AgentPo extends BaseEntity {

    private String name;

    private String description;

    private String systemPrompt;

    /** 绑定的模型配置 ID，FK → t_model_config.id */
    private Long modelConfigId;

    /** 绑定的工作流 ID，NULL 表示不使用工作流 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long workflowId;

    /** 关联知识库 ID 列表，knowledge 模块实现后生效 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> knowledgeBaseIds;

    private java.math.BigDecimal temperature;

    private Integer maxTurns;

    /** 单次回复最大 token 数，NULL 表示用模型默认值 */
    private Integer maxTokens;

    /** 上下文滑动窗口轮数，NULL 表示不裁剪 */
    private Integer maxContextTurns;

    /** 是否启用会话摘要记忆 */
    private Integer memoryEnabled;

    /** 触发摘要更新的消息数阈值 */
    private Integer summaryTriggerMessageCount;

    /** 摘要最大输出 token 数 */
    private Integer summaryMaxTokens;

    /** 摘要模型配置，NULL 表示复用 Agent 聊天模型 */
    private Long summaryModelConfigId;

    private Integer enabled;

    /** 乐观锁版本号，防止并发覆写 */
    @Version
    private Integer version;
}
