-- V36: Knowledge RAG production readiness

ALTER TABLE t_knowledge_base
    ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT/WORKSPACE/PUBLIC' AFTER project_id,
    ADD COLUMN share_scope VARCHAR(32) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT/WORKSPACE/PUBLIC' AFTER visibility;

ALTER TABLE t_knowledge_document
    ADD COLUMN department VARCHAR(64) NOT NULL DEFAULT '' COMMENT '部门元数据' AFTER file_size,
    ADD COLUMN document_type VARCHAR(64) NOT NULL DEFAULT '' COMMENT '文档类型元数据' AFTER department,
    ADD COLUMN tags_json JSON NULL COMMENT '标签元数据' AFTER document_type,
    ADD COLUMN permission_scope VARCHAR(32) NOT NULL DEFAULT 'PROJECT' COMMENT 'PROJECT/WORKSPACE/PUBLIC' AFTER tags_json,
    ADD COLUMN processing_task_id BIGINT NULL COMMENT '当前处理任务 ID' AFTER last_retry_at,
    ADD COLUMN task_status VARCHAR(32) NOT NULL DEFAULT '' COMMENT '当前处理任务状态' AFTER processing_task_id,
    ADD COLUMN progress_message VARCHAR(500) NOT NULL DEFAULT '' COMMENT '处理进度说明' AFTER task_status,
    ADD COLUMN parse_latency_ms BIGINT NULL COMMENT '解析耗时' AFTER progress_message,
    ADD COLUMN chunk_latency_ms BIGINT NULL COMMENT '切片耗时' AFTER parse_latency_ms,
    ADD COLUMN embedding_latency_ms BIGINT NULL COMMENT 'Embedding 耗时' AFTER chunk_latency_ms,
    ADD COLUMN vector_save_latency_ms BIGINT NULL COMMENT '写入 pgvector 耗时' AFTER embedding_latency_ms,
    ADD COLUMN last_processed_chunk_index INT NOT NULL DEFAULT 0 COMMENT '最后完成处理的 chunk index' AFTER vector_save_latency_ms;

CREATE TABLE IF NOT EXISTS t_knowledge_task (
    id                         BIGINT       NOT NULL AUTO_INCREMENT,
    knowledge_base_id          BIGINT       NOT NULL COMMENT '知识库 ID',
    document_id                BIGINT       NULL COMMENT '文档 ID',
    task_type                  VARCHAR(32)  NOT NULL COMMENT 'DOCUMENT_PROCESS/REBUILD_INDEX/REVECTORIZE',
    target_type                VARCHAR(32)  NOT NULL COMMENT 'KNOWLEDGE_BASE/DOCUMENT',
    target_id                  BIGINT       NOT NULL COMMENT '目标 ID',
    reason                     VARCHAR(64)  NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD/MANUAL_REBUILD/EMBEDDING_MODEL_CHANGED/CHUNK_STRATEGY_CHANGED/DOCUMENT_UPDATED',
    status                     VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/DONE/FAILED/CANCELED',
    process_stage              VARCHAR(32)  NOT NULL DEFAULT 'SAVED',
    process_progress           INT          NOT NULL DEFAULT 0,
    progress_message           VARCHAR(500) NOT NULL DEFAULT '',
    attempt                    INT          NOT NULL DEFAULT 0,
    max_attempt                INT          NOT NULL DEFAULT 3,
    last_processed_chunk_index INT          NOT NULL DEFAULT 0,
    cancel_requested           TINYINT(1)   NOT NULL DEFAULT 0,
    error_code                 VARCHAR(64)  NOT NULL DEFAULT '',
    error_message              VARCHAR(1000) NOT NULL DEFAULT '',
    started_at                 DATETIME(3)  NULL,
    finished_at                DATETIME(3)  NULL,
    created_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at                 DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by                 BIGINT       NOT NULL DEFAULT 0,
    deleted                    TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_status_created (status, deleted, created_at),
    INDEX idx_kb_status_created (knowledge_base_id, deleted, status, created_at),
    INDEX idx_doc_created (document_id, deleted, created_at),
    INDEX idx_target_status (target_type, target_id, deleted, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '知识库处理任务';
