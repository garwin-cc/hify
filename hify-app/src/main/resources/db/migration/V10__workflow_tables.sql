-- V10: 工作流定义、节点和连线

CREATE TABLE IF NOT EXISTS t_workflow (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100)  NOT NULL COMMENT '工作流名称',
    description    VARCHAR(500)  NOT NULL DEFAULT '' COMMENT '描述',
    enabled        TINYINT(1)    NOT NULL DEFAULT 1 COMMENT '是否启用',
    start_node_key VARCHAR(100)  NOT NULL COMMENT '开始节点 key',
    created_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT        NOT NULL DEFAULT 0,
    deleted        TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_enabled_deleted (enabled, deleted),
    INDEX idx_name_deleted (name, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流';

CREATE TABLE IF NOT EXISTS t_workflow_node (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_id BIGINT       NOT NULL COMMENT '关联 t_workflow.id',
    node_key    VARCHAR(100) NOT NULL COMMENT '工作流内唯一节点 key',
    node_type   VARCHAR(30)  NOT NULL COMMENT 'START/LLM/CONDITION/TOOL/REPLY/END',
    name        VARCHAR(100) NOT NULL COMMENT '节点名称',
    config      JSON         NOT NULL COMMENT '节点配置',
    position_x  INT          NOT NULL DEFAULT 0 COMMENT '画布 X 坐标',
    position_y  INT          NOT NULL DEFAULT 0 COMMENT '画布 Y 坐标',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NOT NULL DEFAULT 0,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_deleted (workflow_id, deleted),
    UNIQUE KEY uk_workflow_node_key_deleted (workflow_id, node_key, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流节点';

CREATE TABLE IF NOT EXISTS t_workflow_edge (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_id          BIGINT       NOT NULL COMMENT '关联 t_workflow.id',
    source_node_key      VARCHAR(100) NOT NULL COMMENT '起始节点 key',
    target_node_key      VARCHAR(100) NOT NULL COMMENT '目标节点 key',
    edge_type            VARCHAR(30)  NOT NULL DEFAULT 'DEFAULT' COMMENT 'DEFAULT/CONDITION',
    condition_expression VARCHAR(500) NULL COMMENT '条件表达式',
    sort_order           INT          NOT NULL DEFAULT 0,
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by           BIGINT       NOT NULL DEFAULT 0,
    deleted              TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_workflow_source (workflow_id, source_node_key, deleted),
    INDEX idx_workflow_target (workflow_id, target_node_key, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流连线';
