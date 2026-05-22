ALTER TABLE t_message_feedback
    ADD COLUMN project_id BIGINT NULL COMMENT '项目 ID' AFTER agent_id,
    ADD COLUMN trace_id VARCHAR(64) NOT NULL DEFAULT '' COMMENT '对话 traceId' AFTER project_id,
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'OPEN' COMMENT 'OPEN/REVIEWING/RESOLVED/IGNORED' AFTER status,
    ADD COLUMN resolution_note VARCHAR(1000) NOT NULL DEFAULT '' COMMENT '处理备注' AFTER review_status,
    ADD COLUMN resolved_at DATETIME(3) NULL COMMENT '处理完成时间' AFTER resolution_note;

UPDATE t_message_feedback f
LEFT JOIN t_chat_message m ON m.id = f.message_id AND m.deleted = 0
LEFT JOIN t_conversation_trace c ON c.trace_id = m.trace_id AND c.deleted = 0
   SET f.trace_id = COALESCE(NULLIF(m.trace_id, ''), f.trace_id),
       f.project_id = c.project_id,
       f.review_status = CASE WHEN f.rating = 'DISLIKE' THEN 'OPEN' ELSE 'RESOLVED' END
 WHERE f.deleted = 0;

CREATE INDEX idx_message_feedback_project_created ON t_message_feedback (project_id, deleted, created_at);
CREATE INDEX idx_message_feedback_trace ON t_message_feedback (trace_id, deleted);
CREATE INDEX idx_message_feedback_review ON t_message_feedback (review_status, deleted, created_at);
