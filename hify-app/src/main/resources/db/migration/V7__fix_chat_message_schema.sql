-- -----------------------------------------------------------------------------
-- V7: 修正 t_chat_message 结构
--   旧版 V6 用了不同的字段名（token_count / model_config_id），
--   且缺少 status / tool_calls / tool_call_id / latency_ms。
--   此迁移将表结构对齐到当前设计。
-- -----------------------------------------------------------------------------

ALTER TABLE t_chat_message
    -- 删除旧版多余字段
    DROP COLUMN model_config_id,
    DROP COLUMN token_count,

    -- 修正 finish_reason 默认值（旧版 NOT NULL DEFAULT ''，改为 NULL）
    MODIFY COLUMN finish_reason VARCHAR(20) NULL COMMENT 'LLM 停止原因：stop / length / tool_calls / error',

    -- 新增字段
    ADD COLUMN status       VARCHAR(20)  NOT NULL DEFAULT 'DONE'  COMMENT '状态：PENDING / STREAMING / DONE / ERROR' AFTER content,
    ADD COLUMN tool_calls   JSON         NULL                     COMMENT 'assistant 发起工具调用时的调用列表（OpenAI tool_calls 格式）' AFTER status,
    ADD COLUMN tool_call_id VARCHAR(100) NULL                     COMMENT 'role=tool 时，对应的 tool_call.id' AFTER tool_calls,
    ADD COLUMN tokens       INT          NULL                     COMMENT 'token 数，流式生成结束后回填' AFTER tool_call_id,
    ADD COLUMN latency_ms   INT          NULL                     COMMENT '首 token 延迟 ms（仅 assistant 消息）' AFTER finish_reason;
