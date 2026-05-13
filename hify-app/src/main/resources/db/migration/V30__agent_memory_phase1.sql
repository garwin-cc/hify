ALTER TABLE t_agent
    ADD COLUMN memory_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 Agent 会话记忆' AFTER max_context_turns,
    ADD COLUMN summary_trigger_message_count INT NOT NULL DEFAULT 20 COMMENT '触发摘要的消息数阈值' AFTER memory_enabled,
    ADD COLUMN summary_max_tokens INT NOT NULL DEFAULT 800 COMMENT '摘要最大输出 token 数' AFTER summary_trigger_message_count,
    ADD COLUMN summary_model_config_id BIGINT NULL COMMENT '摘要模型配置，NULL 表示复用 Agent 聊天模型' AFTER summary_max_tokens;

CREATE TABLE IF NOT EXISTS t_chat_session_summary (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    agent_id BIGINT NOT NULL COMMENT 'Agent ID',
    summary MEDIUMTEXT NOT NULL COMMENT '会话摘要',
    version INT NOT NULL DEFAULT 1 COMMENT '摘要版本',
    source_message_start_id BIGINT NULL COMMENT '摘要来源起始消息 ID',
    source_message_end_id BIGINT NULL COMMENT '摘要来源结束消息 ID',
    source_message_count INT NOT NULL DEFAULT 0 COMMENT '摘要覆盖消息数',
    status VARCHAR(32) NOT NULL DEFAULT 'DONE' COMMENT 'DONE / FAILED',
    error_message VARCHAR(512) NULL COMMENT '摘要更新失败信息',
    summarized_at DATETIME(3) NOT NULL COMMENT '摘要更新时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_session_active (session_id, deleted),
    INDEX idx_agent_updated (agent_id, deleted, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话摘要记忆';

ALTER TABLE t_conversation_trace
    ADD COLUMN memory_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用记忆' AFTER mcp_triggered,
    ADD COLUMN summary_used TINYINT(1) NOT NULL DEFAULT 0 COMMENT '本次是否使用会话摘要' AFTER memory_enabled,
    ADD COLUMN summary_version INT NULL COMMENT '使用的摘要版本' AFTER summary_used,
    ADD COLUMN summary_latency_ms INT NULL COMMENT '摘要更新耗时 ms' AFTER summary_version,
    ADD COLUMN summary_error_message VARCHAR(512) NULL COMMENT '摘要更新错误' AFTER summary_latency_ms;
