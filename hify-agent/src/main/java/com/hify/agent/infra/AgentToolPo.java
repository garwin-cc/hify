package com.hify.agent.infra;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 关联表 PO — 不继承 BaseEntity。
 * t_agent_tool 是纯关联表，用硬删除语义（更新工具列表时先 DELETE 后 INSERT），
 * 不需要 deleted / version / created_by 等通用字段。
 */
@Data
@TableName("t_agent_tool")
public class AgentToolPo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long agentId;

    private Long toolId;

    private LocalDateTime createdAt;
}
