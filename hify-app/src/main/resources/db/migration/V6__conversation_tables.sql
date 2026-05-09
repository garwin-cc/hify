-- -----------------------------------------------------------------------------
-- V6: 对话引擎核心表
--   t_chat_session  — 会话（一个用户与一个 Agent 的一次完整对话）
--   t_chat_message  — 消息（会话内每一条 user / assistant / tool 消息）
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS t_chat_session (
    id              BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    agent_id        BIGINT       NOT NULL                              COMMENT '关联 t_agent.id',
    user_id         BIGINT       NOT NULL DEFAULT 0                    COMMENT '用户 ID，Auth 模块接入前默认 0',
    title           VARCHAR(200) NOT NULL DEFAULT '新对话'             COMMENT '会话标题，取首条用户消息前 20 字自动生成',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'             COMMENT '状态：ACTIVE / ARCHIVED',
    message_count   INT          NOT NULL DEFAULT 0                    COMMENT '消息条数（冗余计数，避免 COUNT(*) 扫表）',
    last_message_at DATETIME(3)  NULL                                  COMMENT '最后一条消息时间，用于列表排序',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_user_last    (user_id,  deleted, last_message_at),
    INDEX idx_agent        (agent_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话会话';

CREATE TABLE IF NOT EXISTS t_chat_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    session_id      BIGINT       NOT NULL                              COMMENT '关联 t_chat_session.id',
    role            VARCHAR(20)  NOT NULL                              COMMENT '角色：user / assistant / tool',
    content         MEDIUMTEXT   NOT NULL                              COMMENT '消息正文',
    status          VARCHAR(20)  NOT NULL DEFAULT 'DONE'               COMMENT '状态：PENDING / STREAMING / DONE / ERROR',
    tool_calls      JSON         NULL                                  COMMENT 'assistant 发起工具调用时的调用列表（OpenAI tool_calls 格式）',
    tool_call_id    VARCHAR(100) NULL                                  COMMENT 'role=tool 时，对应的 tool_call.id',
    tokens          INT          NULL                                  COMMENT 'token 数，流式生成结束后回填',
    finish_reason   VARCHAR(20)  NULL                                  COMMENT 'LLM 停止原因：stop / length / tool_calls / error',
    latency_ms      INT          NULL                                  COMMENT '首 token 延迟 ms（仅 assistant 消息）',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_session_created (session_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话消息';
