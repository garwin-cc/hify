-- V32: 项目级权限基础和审计日志。先兼容迁移，不启用强权限拦截。

CREATE TABLE IF NOT EXISTS t_workspace (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '空间名称',
    code        VARCHAR(64)  NOT NULL COMMENT '空间编码',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NOT NULL DEFAULT 0,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_code_deleted (code, deleted),
    INDEX idx_workspace_status_created (status, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '组织空间';

CREATE TABLE IF NOT EXISTS t_project (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT       NOT NULL COMMENT '空间 ID',
    name         VARCHAR(100) NOT NULL COMMENT '项目名称',
    code         VARCHAR(64)  NOT NULL COMMENT '项目编码',
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT       NOT NULL DEFAULT 0,
    deleted      TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_code_deleted (workspace_id, code, deleted),
    INDEX idx_project_workspace_status (workspace_id, deleted, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目';

CREATE TABLE IF NOT EXISTS t_project_member (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    workspace_id BIGINT      NOT NULL COMMENT '空间 ID',
    project_id   BIGINT      NOT NULL COMMENT '项目 ID',
    user_id      BIGINT      NOT NULL COMMENT '用户 ID',
    role         VARCHAR(20) NOT NULL COMMENT 'OWNER/DEVELOPER/OPERATOR/REVIEWER/VIEWER',
    status       VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
    created_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by   BIGINT      NOT NULL DEFAULT 0,
    deleted      TINYINT(1)  NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_project_user_deleted (project_id, user_id, deleted),
    INDEX idx_user_project_status (user_id, deleted, status, project_id),
    INDEX idx_project_role_status (project_id, deleted, role, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '项目成员';

CREATE TABLE IF NOT EXISTS t_identity_provider (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    name           VARCHAR(100) NOT NULL COMMENT '身份源名称',
    type           VARCHAR(20)  NOT NULL COMMENT 'OIDC/LDAP/SAML',
    issuer_url     VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'OIDC issuer 或 SAML issuer',
    client_id      VARCHAR(128) NOT NULL DEFAULT '' COMMENT 'OIDC/SAML client id',
    client_secret  VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'OIDC/SAML client secret',
    ldap_url       VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'LDAP 地址',
    ldap_base_dn   VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'LDAP base DN',
    enabled        TINYINT(1)   NOT NULL DEFAULT 0,
    config_json    JSON         NOT NULL COMMENT '扩展配置，保存 scope、claim、LDAP filter 等',
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT       NOT NULL DEFAULT 0,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_identity_provider_name_deleted (name, deleted),
    INDEX idx_identity_provider_type_enabled (type, deleted, enabled)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '企业身份源配置';

CREATE TABLE IF NOT EXISTS t_external_identity (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    user_id              BIGINT       NOT NULL COMMENT 'Hify 用户 ID',
    identity_provider_id BIGINT       NOT NULL COMMENT '身份源 ID',
    provider_type        VARCHAR(20)  NOT NULL COMMENT 'OIDC/LDAP/SAML',
    external_subject     VARCHAR(255) NOT NULL COMMENT '外部唯一主体',
    external_username    VARCHAR(128) NOT NULL DEFAULT '' COMMENT '外部用户名',
    email                VARCHAR(128) NOT NULL DEFAULT '' COMMENT '外部邮箱',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by           BIGINT       NOT NULL DEFAULT 0,
    deleted              TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_external_identity_subject (identity_provider_id, external_subject, deleted),
    INDEX idx_external_identity_user (user_id, deleted, identity_provider_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '外部身份映射';

CREATE TABLE IF NOT EXISTS t_audit_log (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    trace_id        VARCHAR(64)  NOT NULL DEFAULT '' COMMENT 'traceId',
    actor_user_id   BIGINT       NULL COMMENT '操作人 ID',
    actor_username  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '操作人登录名',
    workspace_id    BIGINT       NULL COMMENT '空间 ID',
    project_id      BIGINT       NULL COMMENT '项目 ID',
    action          VARCHAR(64)  NOT NULL COMMENT '操作类型',
    resource_type   VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '资源类型',
    resource_id     BIGINT       NULL COMMENT '资源 ID',
    resource_name   VARCHAR(200) NOT NULL DEFAULT '' COMMENT '资源名称',
    request_method  VARCHAR(16)  NOT NULL DEFAULT '' COMMENT 'HTTP 方法',
    request_path    VARCHAR(255) NOT NULL DEFAULT '' COMMENT 'HTTP 路径',
    client_ip       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '客户端 IP',
    user_agent      VARCHAR(512) NOT NULL DEFAULT '' COMMENT 'User-Agent',
    success         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否成功',
    error_code      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '错误码',
    error_message   VARCHAR(512) NOT NULL DEFAULT '' COMMENT '错误信息',
    before_json     JSON         NOT NULL COMMENT '变更前快照，敏感字段脱敏',
    after_json      JSON         NOT NULL COMMENT '变更后快照，敏感字段脱敏',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_actor_created (actor_user_id, deleted, created_at),
    INDEX idx_project_created (project_id, deleted, created_at),
    INDEX idx_action_created (action, deleted, created_at),
    INDEX idx_resource_created (resource_type, resource_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '审计日志';

INSERT INTO t_workspace (id, name, code, status, created_by, deleted)
SELECT 1, '默认空间', 'default', 'ACTIVE', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM t_workspace WHERE id = 1);

INSERT INTO t_project (id, workspace_id, name, code, status, created_by, deleted)
SELECT 1, 1, '默认项目', 'default', 'ACTIVE', 0, 0
WHERE NOT EXISTS (SELECT 1 FROM t_project WHERE id = 1);

INSERT INTO t_project_member (workspace_id, project_id, user_id, role, status, created_by, deleted)
SELECT 1,
       1,
       u.id,
       CASE u.role
           WHEN 'ADMIN' THEN 'OWNER'
           WHEN 'EDITOR' THEN 'DEVELOPER'
           ELSE 'VIEWER'
       END,
       'ACTIVE',
       0,
       0
FROM t_user u
WHERE u.deleted = 0
  AND NOT EXISTS (
      SELECT 1
      FROM t_project_member pm
      WHERE pm.project_id = 1
        AND pm.user_id = u.id
        AND pm.deleted = 0
  );

ALTER TABLE t_agent
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '空间 ID' AFTER id,
    ADD COLUMN project_id BIGINT NOT NULL DEFAULT 1 COMMENT '项目 ID' AFTER workspace_id;

ALTER TABLE t_knowledge_base
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '空间 ID' AFTER id,
    ADD COLUMN project_id BIGINT NOT NULL DEFAULT 1 COMMENT '项目 ID' AFTER workspace_id;

ALTER TABLE t_workflow
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '空间 ID' AFTER id,
    ADD COLUMN project_id BIGINT NOT NULL DEFAULT 1 COMMENT '项目 ID' AFTER workspace_id;

ALTER TABLE t_workflow_template
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '空间 ID' AFTER id,
    ADD COLUMN project_id BIGINT NOT NULL DEFAULT 1 COMMENT '项目 ID' AFTER workspace_id;

ALTER TABLE t_mcp_server
    ADD COLUMN workspace_id BIGINT NOT NULL DEFAULT 1 COMMENT '空间 ID' AFTER id,
    ADD COLUMN project_id BIGINT NOT NULL DEFAULT 1 COMMENT '项目 ID' AFTER workspace_id;

CREATE INDEX idx_agent_project_created ON t_agent (project_id, deleted, created_at);
CREATE INDEX idx_kb_project_created ON t_knowledge_base (project_id, deleted, created_at);
CREATE INDEX idx_workflow_project_created ON t_workflow (project_id, deleted, created_at);
CREATE INDEX idx_workflow_template_project_created ON t_workflow_template (project_id, deleted, created_at);
CREATE INDEX idx_mcp_server_project_created ON t_mcp_server (project_id, deleted, created_at);
