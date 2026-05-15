ALTER TABLE t_knowledge_chunk
    ADD COLUMN IF NOT EXISTS index_version BIGINT NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT true;

DROP INDEX IF EXISTS uk_kb_doc_chunk_active;

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_doc_chunk_version_active
    ON t_knowledge_chunk (knowledge_base_id, document_id, chunk_index, index_version)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_kb_active_version
    ON t_knowledge_chunk (knowledge_base_id, active, index_version, deleted);
