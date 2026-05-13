ALTER TABLE t_chat_message
    ADD COLUMN trace_id VARCHAR(64) NULL COMMENT '对话请求 traceId' AFTER session_id,
    ADD COLUMN error_code VARCHAR(64) NULL COMMENT '对话失败错误码' AFTER finish_reason,
    ADD COLUMN error_message VARCHAR(512) NULL COMMENT '面向用户的错误信息' AFTER error_code,
    ADD COLUMN debug_error MEDIUMTEXT NULL COMMENT '脱敏后的排障错误信息' AFTER error_message,
    ADD COLUMN partial TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为未完成的部分输出' AFTER debug_error,
    ADD COLUMN total_latency_ms INT NULL COMMENT '总耗时 ms' AFTER latency_ms,
    ADD INDEX idx_trace_id (trace_id, deleted);

CREATE TABLE IF NOT EXISTS t_conversation_trace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL COMMENT '统一 traceId',
    session_id BIGINT NOT NULL COMMENT '会话 ID',
    user_message_id BIGINT NOT NULL COMMENT '用户消息 ID',
    assistant_message_id BIGINT NULL COMMENT '助手消息 ID',
    agent_id BIGINT NOT NULL COMMENT 'Agent ID',
    agent_name VARCHAR(128) NULL COMMENT 'Agent 名称',
    model_config_id BIGINT NULL COMMENT '模型配置 ID',
    provider_id BIGINT NULL COMMENT 'Provider ID',
    provider_name VARCHAR(128) NULL COMMENT 'Provider 名称',
    provider_type VARCHAR(32) NULL COMMENT 'Provider 类型',
    model_id VARCHAR(128) NULL COMMENT '供应商模型 ID',
    workflow_id BIGINT NULL COMMENT '工作流 ID',
    workflow_run_id BIGINT NULL COMMENT '工作流运行 ID',
    rag_triggered TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否触发 RAG',
    mcp_triggered TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否触发 MCP',
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / DONE / ERROR / TIMEOUT / CLIENT_DISCONNECTED / BACKEND_ERROR',
    error_code VARCHAR(64) NULL COMMENT '错误码',
    error_message VARCHAR(512) NULL COMMENT '用户可读错误信息',
    started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    first_token_at DATETIME(3) NULL COMMENT '首 token 时间',
    finished_at DATETIME(3) NULL COMMENT '结束时间',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trace_id (trace_id),
    INDEX idx_session_created (session_id, deleted, created_at),
    INDEX idx_agent_created (agent_id, deleted, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话运行 Trace';

CREATE TABLE IF NOT EXISTS t_conversation_rag_trace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL COMMENT '统一 traceId',
    knowledge_base_id BIGINT NOT NULL COMMENT '知识库 ID',
    knowledge_base_name VARCHAR(128) NULL COMMENT '知识库名称',
    document_id VARCHAR(128) NULL COMMENT '文档 ID',
    document_name VARCHAR(255) NULL COMMENT '文档名称',
    chunk_id BIGINT NULL COMMENT 'chunk ID',
    chunk_index INT NULL COMMENT 'chunk 序号',
    score DECIMAL(10,6) NULL COMMENT '最终相关性分数',
    content_preview VARCHAR(512) NULL COMMENT 'chunk 内容预览',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_trace_score (trace_id, deleted, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话 RAG 命中 Trace';

CREATE TABLE IF NOT EXISTS t_conversation_llm_trace (
    id BIGINT NOT NULL AUTO_INCREMENT,
    trace_id VARCHAR(64) NOT NULL COMMENT '统一 traceId',
    provider_id BIGINT NULL COMMENT 'Provider ID',
    provider_name VARCHAR(128) NULL COMMENT 'Provider 名称',
    provider_type VARCHAR(32) NULL COMMENT 'Provider 类型',
    model_config_id BIGINT NULL COMMENT '模型配置 ID',
    model_id VARCHAR(128) NULL COMMENT '供应商模型 ID',
    streaming TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否流式调用',
    input_tokens INT NULL COMMENT '输入 token 数',
    output_tokens INT NULL COMMENT '输出 token 数',
    first_token_latency_ms INT NULL COMMENT '首 token 延迟 ms',
    total_latency_ms INT NULL COMMENT '总耗时 ms',
    status VARCHAR(32) NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING / DONE / ERROR / TIMEOUT',
    error_code VARCHAR(64) NULL COMMENT '错误码',
    error_message VARCHAR(512) NULL COMMENT '错误信息',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT NOT NULL DEFAULT 0,
    deleted TINYINT(1) NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_trace_id (trace_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话 LLM 调用 Trace';

ALTER TABLE t_mcp_tool_call_audit
    ADD COLUMN trace_id VARCHAR(64) NULL COMMENT '统一 traceId' AFTER id,
    ADD INDEX idx_trace_created (trace_id, deleted, created_at);
