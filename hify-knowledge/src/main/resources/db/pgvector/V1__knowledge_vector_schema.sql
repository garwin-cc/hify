CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS t_knowledge_chunk (
    id                BIGSERIAL PRIMARY KEY,
    knowledge_base_id BIGINT       NOT NULL,
    document_id       VARCHAR(200) NOT NULL,
    chunk_index       INTEGER      NOT NULL,
    content           TEXT         NOT NULL,
    metadata          JSONB        NOT NULL DEFAULT '{}'::jsonb,
    embedding         VECTOR(1536) NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted           BOOLEAN      NOT NULL DEFAULT false
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_doc_chunk_active
    ON t_knowledge_chunk (knowledge_base_id, document_id, chunk_index)
    WHERE deleted = false;

CREATE INDEX IF NOT EXISTS idx_kb_deleted_created
    ON t_knowledge_chunk (knowledge_base_id, deleted, created_at);

CREATE INDEX IF NOT EXISTS idx_embedding_ivfflat
    ON t_knowledge_chunk
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
