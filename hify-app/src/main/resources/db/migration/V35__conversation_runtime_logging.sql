-- V35: Conversation runtime logging, cursor pagination support, feedback, and rate-limit context.

ALTER TABLE t_chat_session
    ADD COLUMN app_id BIGINT NULL COMMENT 'Agent App ID' AFTER user_id,
    ADD COLUMN api_key_id BIGINT NULL COMMENT 'Agent API Key ID' AFTER app_id;

ALTER TABLE t_conversation_trace
    ADD COLUMN user_id BIGINT NULL COMMENT '用户 ID' AFTER session_id,
    ADD COLUMN app_id BIGINT NULL COMMENT 'Agent App ID' AFTER user_id,
    ADD COLUMN api_key_id BIGINT NULL COMMENT 'Agent API Key ID' AFTER app_id,
    ADD INDEX idx_log_query (user_id, agent_id, status, deleted, started_at);

CREATE TABLE IF NOT EXISTS t_message_feedback (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    message_id         BIGINT       NOT NULL COMMENT 'assistant message ID',
    session_id         BIGINT       NOT NULL COMMENT '会话 ID',
    agent_id           BIGINT       NOT NULL COMMENT 'Agent ID',
    user_id            BIGINT       NOT NULL DEFAULT 0 COMMENT '反馈用户',
    rating             VARCHAR(20)  NOT NULL DEFAULT '' COMMENT 'LIKE/DISLIKE',
    issue_type         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '问题类型',
    comment            VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '反馈说明',
    corrected_answer   MEDIUMTEXT   NULL COMMENT '人工修正答案',
    status             VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by         BIGINT       NOT NULL DEFAULT 0,
    deleted            TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_user_deleted (message_id, user_id, deleted),
    INDEX idx_agent_created (agent_id, deleted, created_at),
    INDEX idx_session_created (session_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话消息用户反馈';
