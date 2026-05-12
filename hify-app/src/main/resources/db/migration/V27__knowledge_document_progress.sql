-- V27: knowledge document processing progress

ALTER TABLE t_knowledge_document
    ADD COLUMN process_stage VARCHAR(30) NOT NULL DEFAULT '' COMMENT 'SAVED/EXTRACTING/CHUNKING/EMBEDDING/SAVING/DONE/FAILED' AFTER parse_status,
    ADD COLUMN process_progress INT NOT NULL DEFAULT 0 COMMENT 'processing progress percentage' AFTER process_stage,
    ADD COLUMN processed_chunk_count INT NOT NULL DEFAULT 0 COMMENT 'chunks processed during current task' AFTER process_progress;

