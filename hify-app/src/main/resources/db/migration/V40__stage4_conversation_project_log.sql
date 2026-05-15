ALTER TABLE t_conversation_trace
    ADD COLUMN project_id BIGINT NULL COMMENT '项目 ID' AFTER user_id,
    ADD INDEX idx_project_log_query (project_id, agent_id, status, deleted, started_at);
