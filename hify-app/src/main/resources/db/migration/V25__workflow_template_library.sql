-- V25: 工作流模板库产品化，增加模板版本、使用记录和来源版本。

ALTER TABLE t_workflow_template
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
    ADD COLUMN current_version_id BIGINT NULL COMMENT '当前发布版本 ID',
    ADD COLUMN latest_version_no INT NOT NULL DEFAULT 0 COMMENT '最新版本号',
    ADD COLUMN tags_json JSON NULL COMMENT '模板标签',
    ADD COLUMN node_count INT NOT NULL DEFAULT 0 COMMENT '节点数量',
    ADD COLUMN node_types_json JSON NULL COMMENT '节点类型列表',
    ADD COLUMN requirement_count INT NOT NULL DEFAULT 0 COMMENT '资源需求数量',
    ADD COLUMN usage_count INT NOT NULL DEFAULT 0 COMMENT '使用次数',
    ADD COLUMN last_used_at DATETIME(3) NULL COMMENT '最近使用时间',
    ADD COLUMN created_from_workflow_id BIGINT NULL COMMENT '来源工作流 ID',
    ADD COLUMN published_at DATETIME(3) NULL COMMENT '发布时间',
    ADD COLUMN archived_at DATETIME(3) NULL COMMENT '归档时间';

CREATE TABLE IF NOT EXISTS t_workflow_template_version (
    id                     BIGINT        NOT NULL AUTO_INCREMENT,
    template_id            BIGINT        NOT NULL COMMENT '关联 t_workflow_template.id',
    version_no             INT           NOT NULL COMMENT '模板版本号',
    snapshot_json          MEDIUMTEXT    NOT NULL COMMENT '模板完整快照',
    requirements_json      JSON          NOT NULL COMMENT '资源需求快照',
    node_count             INT           NOT NULL DEFAULT 0,
    node_types_json        JSON          NULL,
    checksum               VARCHAR(64)   NOT NULL DEFAULT '',
    changelog              VARCHAR(1000) NOT NULL DEFAULT '',
    validation_status      VARCHAR(20)   NOT NULL DEFAULT 'PASSED',
    validation_errors_json JSON          NULL,
    published_by           BIGINT        NOT NULL DEFAULT 0,
    published_at           DATETIME(3)   NULL,
    created_at             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at             DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by             BIGINT        NOT NULL DEFAULT 0,
    deleted                TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_template_version (template_id, version_no, deleted),
    INDEX idx_template_created (template_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流模板版本';

CREATE TABLE IF NOT EXISTS t_workflow_template_usage (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    template_id BIGINT      NOT NULL,
    version_id  BIGINT      NOT NULL,
    workflow_id BIGINT      NULL,
    action_type VARCHAR(20) NOT NULL COMMENT 'USE/EXPORT/IMPORT/COPY',
    created_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT      NOT NULL DEFAULT 0,
    deleted     TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_template_created (template_id, deleted, created_at),
    INDEX idx_workflow_deleted (workflow_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流模板使用记录';

ALTER TABLE t_workflow
    ADD COLUMN source_template_version_id BIGINT NULL COMMENT '来源模板版本 ID' AFTER template_id;

UPDATE t_workflow_template
SET status = CASE WHEN enabled = 1 THEN 'PUBLISHED' ELSE 'DRAFT' END,
    node_count = COALESCE(JSON_LENGTH(JSON_EXTRACT(config_json, '$.nodes')), 0),
    requirement_count = 0,
    latest_version_no = 0
WHERE deleted = 0;

INSERT INTO t_workflow_template_version (
    template_id,
    version_no,
    snapshot_json,
    requirements_json,
    node_count,
    node_types_json,
    checksum,
    changelog,
    validation_status,
    published_at
)
SELECT id,
       1,
       config_json,
       COALESCE(JSON_EXTRACT(config_json, '$.requirements'), JSON_OBJECT('models', JSON_ARRAY(), 'knowledgeBases', JSON_ARRAY(), 'tools', JSON_ARRAY())),
       node_count,
       JSON_ARRAY(),
       SHA2(config_json, 256),
       '历史模板初始化',
       'PASSED',
       created_at
FROM t_workflow_template
WHERE deleted = 0;

UPDATE t_workflow_template t
    JOIN t_workflow_template_version v
      ON v.template_id = t.id AND v.version_no = 1 AND v.deleted = 0
SET t.current_version_id = v.id,
    t.latest_version_no = 1
WHERE t.deleted = 0
  AND t.current_version_id IS NULL;
