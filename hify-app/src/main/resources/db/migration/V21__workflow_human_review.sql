-- V21: HUMAN_REVIEW 节点暂停、人工评审和恢复执行所需表结构。

ALTER TABLE t_workflow_run
    ADD COLUMN context_snapshot JSON NULL COMMENT '工作流暂停时的上下文快照' AFTER run_mode;

CREATE TABLE IF NOT EXISTS t_workflow_review_task (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    workflow_run_id   BIGINT       NOT NULL COMMENT '关联 t_workflow_run.id',
    node_key          VARCHAR(100) NOT NULL COMMENT 'HUMAN_REVIEW 节点 key',
    status            VARCHAR(20)  NOT NULL COMMENT 'WAITING/APPROVE/REJECT',
    title             VARCHAR(200) NOT NULL COMMENT '评审标题',
    content           MEDIUMTEXT   NULL COMMENT '评审内容',
    actions_json      JSON         NULL COMMENT '可选动作列表',
    allow_edit        TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否允许评审时编辑内容',
    output_variable   VARCHAR(100) NOT NULL DEFAULT 'result' COMMENT '写回上下文的变量名',
    review_action     VARCHAR(30)  NULL COMMENT '实际评审动作',
    review_comment    VARCHAR(500) NULL COMMENT '评审备注',
    edited_content    MEDIUMTEXT   NULL COMMENT '评审编辑后的内容',
    reviewed_by       VARCHAR(100) NULL COMMENT '评审人',
    reviewed_at       DATETIME(3)  NULL COMMENT '评审时间',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by        BIGINT       NOT NULL DEFAULT 0,
    deleted           TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_review_run_status (workflow_run_id, status, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流人工评审任务';
