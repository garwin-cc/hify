-- V8: 知识库管理基础表

CREATE TABLE IF NOT EXISTS t_knowledge_base (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL COMMENT '知识库名称',
    description    VARCHAR(500) NOT NULL DEFAULT '' COMMENT '描述',
    enabled        TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    document_count INT          NOT NULL DEFAULT 0 COMMENT '文档数冗余统计',
    chunk_count    INT          NOT NULL DEFAULT 0 COMMENT '分块数冗余统计',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT       NOT NULL DEFAULT 0,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_enabled_deleted (enabled, deleted),
    INDEX idx_name_deleted (name, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '知识库';

CREATE TABLE IF NOT EXISTS t_knowledge_document (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    knowledge_base_id BIGINT        NOT NULL COMMENT '关联 t_knowledge_base.id',
    name              VARCHAR(200)  NOT NULL COMMENT '原始文件名',
    file_key          VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '本地或对象存储路径',
    file_type         VARCHAR(50)   NOT NULL DEFAULT '' COMMENT 'pdf/docx/txt/md/html',
    file_size         BIGINT        NOT NULL DEFAULT 0,
    parse_status      VARCHAR(30)   NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING/PARSING/CHUNKING/EMBEDDING/DONE/FAILED',
    chunk_count       INT           NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000) NOT NULL DEFAULT '',
    created_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by        BIGINT        NOT NULL DEFAULT 0,
    deleted           TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_kb_deleted_created (knowledge_base_id, deleted, created_at),
    INDEX idx_status_deleted (parse_status, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '知识库文档';
