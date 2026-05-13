-- V28: knowledge document processing reliability fields

ALTER TABLE t_knowledge_document
    ADD COLUMN error_code VARCHAR(64) NOT NULL DEFAULT '' COMMENT 'structured processing error code' AFTER error_message,
    ADD COLUMN failed_stage VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'stage where processing failed' AFTER error_code,
    ADD COLUMN retryable TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether failed document can be retried' AFTER failed_stage,
    ADD COLUMN cancel_requested TINYINT(1) NOT NULL DEFAULT 0 COMMENT 'whether user requested cancellation' AFTER retryable,
    ADD COLUMN started_at DATETIME(3) NULL COMMENT 'processing start time' AFTER cancel_requested,
    ADD COLUMN finished_at DATETIME(3) NULL COMMENT 'processing finish time' AFTER started_at,
    ADD COLUMN retry_count INT NOT NULL DEFAULT 0 COMMENT 'document processing retry count' AFTER finished_at,
    ADD COLUMN last_retry_at DATETIME(3) NULL COMMENT 'last retry time' AFTER retry_count;

ALTER TABLE t_knowledge_document
    ADD INDEX idx_kb_status_created (knowledge_base_id, deleted, parse_status, created_at);
