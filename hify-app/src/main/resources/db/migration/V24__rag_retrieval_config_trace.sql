-- V24: RAG 检索配置和检索 trace

ALTER TABLE t_knowledge_base
    ADD COLUMN retrieval_mode VARCHAR(32) NOT NULL DEFAULT 'VECTOR' COMMENT 'VECTOR/HYBRID',
    ADD COLUMN top_k INT NOT NULL DEFAULT 5 COMMENT '最终注入 chunk 数',
    ADD COLUMN candidate_top_k INT NOT NULL DEFAULT 20 COMMENT '候选召回 chunk 数',
    ADD COLUMN score_threshold DECIMAL(6,5) NOT NULL DEFAULT 0.65000 COMMENT '最低相关分',
    ADD COLUMN chunk_size INT NOT NULL DEFAULT 512 COMMENT '文档分块大小',
    ADD COLUMN chunk_overlap INT NOT NULL DEFAULT 64 COMMENT '分块重叠 token 数',
    ADD COLUMN max_context_tokens INT NOT NULL DEFAULT 3000 COMMENT 'RAG 上下文 token 预算',
    ADD COLUMN rerank_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用 rerank',
    ADD COLUMN rerank_model_config_id BIGINT NULL COMMENT 'rerank 模型配置 ID',
    ADD COLUMN rerank_top_n INT NOT NULL DEFAULT 20 COMMENT 'rerank 候选数量';

CREATE TABLE IF NOT EXISTS t_rag_retrieval_trace (
    id                       BIGINT       NOT NULL AUTO_INCREMENT,
    trace_id                 VARCHAR(64)  NOT NULL COMMENT '检索 trace ID',
    source_type              VARCHAR(32)  NOT NULL DEFAULT 'UNKNOWN' COMMENT 'CONVERSATION/WORKFLOW/TEST',
    source_id                VARCHAR(128) NOT NULL DEFAULT '' COMMENT '来源业务 ID',
    agent_id                 BIGINT       NULL COMMENT 'Agent ID',
    query_text               MEDIUMTEXT   NOT NULL COMMENT '原始查询文本',
    knowledge_base_ids_json  JSON         NOT NULL COMMENT '知识库 ID 列表',
    retrieval_mode           VARCHAR(32)  NOT NULL DEFAULT 'VECTOR',
    top_k                    INT          NULL,
    score_threshold          DECIMAL(6,5) NULL,
    rerank_enabled           TINYINT(1)   NOT NULL DEFAULT 0,
    selected_chunk_ids_json  JSON         NOT NULL COMMENT '命中 chunk ID 列表',
    hit_count                INT          NOT NULL DEFAULT 0,
    latency_ms               BIGINT       NOT NULL DEFAULT 0,
    status                   VARCHAR(32)  NOT NULL DEFAULT 'SUCCESS',
    error_message            MEDIUMTEXT   NULL,
    created_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at               DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by               BIGINT       NOT NULL DEFAULT 0,
    deleted                  TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_trace_id (trace_id),
    INDEX idx_source_created (source_type, deleted, created_at),
    INDEX idx_agent_created (agent_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'RAG 检索 trace';
