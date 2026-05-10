-- V22: CODE_TASK 节点、节点调试和工作流版本快照。

CREATE TABLE IF NOT EXISTS t_workflow_version (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_id    BIGINT       NOT NULL COMMENT '关联 t_workflow.id',
    version_no     INT          NOT NULL COMMENT '工作流版本号',
    snapshot_json  JSON         NOT NULL COMMENT 'workflow + nodes + edges 完整快照',
    change_summary VARCHAR(500) NOT NULL DEFAULT '' COMMENT '变更摘要',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT       NOT NULL DEFAULT 0,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workflow_version_deleted (workflow_id, version_no, deleted),
    INDEX idx_workflow_created (workflow_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流版本快照';

ALTER TABLE t_workflow_run
    ADD COLUMN workflow_version_id BIGINT NULL COMMENT '本次执行使用的工作流版本 ID' AFTER workflow_id;
