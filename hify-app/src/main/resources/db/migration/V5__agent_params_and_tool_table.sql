-- ① t_agent: temperature / max_turns nullable + 두 개 컬럼 추가
ALTER TABLE t_agent
    MODIFY COLUMN temperature     DECIMAL(4,2) NULL    COMMENT '0.00–2.00，NULL 表示用模型默认值',
    MODIFY COLUMN max_turns       INT          NULL    COMMENT '最大对话轮数，NULL 表示不限制',
    ADD COLUMN   max_tokens       INT          NULL    COMMENT '单次回复最大 token 数，NULL 表示用模型默认值'
                                                       AFTER max_turns,
    ADD COLUMN   max_context_turns INT         NULL    COMMENT '上下文滑动窗口轮数，NULL 表示不裁剪'
                                                       AFTER max_tokens;

-- ② t_agent_tool: 当前结构含 deleted/enabled/tool_name 等不属于关联表的字段，整体重建
--   关联表用硬删除语义（删了重插），不需要 BaseEntity 的通用字段
DROP TABLE IF EXISTS t_agent_tool;

CREATE TABLE t_agent_tool (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    agent_id      BIGINT       NOT NULL,
    mcp_server_id BIGINT       NOT NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool   (agent_id, mcp_server_id),
    INDEX        idx_mcp_server (mcp_server_id)        -- 反向查：哪些 Agent 使用了某工具
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 与 MCP 工具关联';
