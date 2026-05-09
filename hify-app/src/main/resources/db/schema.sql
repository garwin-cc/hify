-- =============================================================================
-- Hify 数据库初始化脚本
-- 数据库：utf8mb4 / utf8mb4_unicode_ci
-- 规范：id BIGINT 自增、datetime(3) 时间、deleted TINYINT(1) 逻辑删除
-- 外键关系由应用层维护，不加 FOREIGN KEY 约束（避免大表 DDL 锁）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. t_provider — LLM 提供商配置
--    对应 hify-model 模块，管理 OpenAI / Claude / Gemini / Ollama 等接入信息
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_provider (
    id         BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    name       VARCHAR(100) NOT NULL                              COMMENT '显示名称，如 "OpenAI 官方"',
    type       VARCHAR(50)  NOT NULL                              COMMENT '类型枚举：OPENAI / CLAUDE / GEMINI / OLLAMA',
    api_key    VARCHAR(500) NOT NULL DEFAULT ''                   COMMENT 'API 密钥，Ollama 等本地部署留空',
    base_url   VARCHAR(500) NOT NULL DEFAULT ''                   COMMENT '自定义接入点，使用官方地址时留空',
    enabled    TINYINT(1)   NOT NULL DEFAULT 1                    COMMENT '是否启用：1 启用 / 0 停用',
    created_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by BIGINT       NOT NULL DEFAULT 0,
    deleted    TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 按类型查询提供商列表，deleted 放后（区分度低）
    INDEX idx_type_deleted (type, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'LLM 提供商配置';


-- -----------------------------------------------------------------------------
-- 2. t_model_config — 模型配置
--    同一提供商下可配置多个模型（gpt-4o / gpt-4o-mini 等），参数各自独立
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_model_config (
    id             BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    provider_id    BIGINT       NOT NULL                              COMMENT '关联 t_provider.id',
    model_id       VARCHAR(100) NOT NULL                              COMMENT '模型标识，如 gpt-4o、claude-3-5-sonnet-20241022',
    display_name   VARCHAR(100) NOT NULL                              COMMENT '界面显示名称',
    context_window INT          NOT NULL DEFAULT 8192                 COMMENT '上下文窗口大小（token 数）',
    max_tokens     INT          NOT NULL DEFAULT 2048                 COMMENT '单次最大输出 token 数',
    enabled        TINYINT(1)   NOT NULL DEFAULT 1,
    created_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by     BIGINT       NOT NULL DEFAULT 0,
    deleted        TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 按提供商过滤模型列表
    INDEX idx_provider_deleted (provider_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '模型配置（隶属于某一提供商）';


-- -----------------------------------------------------------------------------
-- 3. t_mcp_server — MCP 工具服务配置
--    记录外部 MCP 服务的接入地址与认证信息
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_mcp_server (
    id          BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    name        VARCHAR(100) NOT NULL                              COMMENT 'MCP 服务名称',
    description VARCHAR(500) NOT NULL DEFAULT ''                   COMMENT '描述',
    endpoint    VARCHAR(500) NOT NULL                              COMMENT 'MCP 服务端点 URL',
    auth_type   VARCHAR(50)  NOT NULL DEFAULT 'NONE'               COMMENT '认证类型：NONE / API_KEY / BEARER',
    auth_config TEXT         NOT NULL                              COMMENT '认证参数 JSON，如 {"key":"xxx"}；NONE 时填 {}',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NOT NULL DEFAULT 0,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_enabled_deleted (enabled, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MCP 工具服务配置';


-- -----------------------------------------------------------------------------
-- 4. t_agent — Agent 配置
--    核心业务表；version 字段配合 @Version 实现并发更新乐观锁
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent (
    id              BIGINT        NOT NULL AUTO_INCREMENT               COMMENT '主键',
    name            VARCHAR(100)  NOT NULL                              COMMENT 'Agent 名称',
    description     VARCHAR(500)  NOT NULL DEFAULT ''                   COMMENT '描述',
    system_prompt   MEDIUMTEXT    NOT NULL                              COMMENT '系统提示词，内容可能很长',
    model_config_id BIGINT        NOT NULL                              COMMENT '关联 t_model_config.id',
    temperature     DECIMAL(4, 2) NOT NULL DEFAULT 0.70                 COMMENT '采样温度 0.00-2.00',
    max_turns       INT           NOT NULL DEFAULT 20                   COMMENT '单次会话最大对话轮数',
    enabled         TINYINT(1)    NOT NULL DEFAULT 1,
    version         INT           NOT NULL DEFAULT 0                    COMMENT 'MyBatis-Plus @Version 乐观锁',
    created_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT        NOT NULL DEFAULT 0,
    deleted         TINYINT(1)    NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 列表页常用过滤：启用状态 + 未删除
    INDEX idx_enabled_deleted (enabled, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 配置（乐观锁 version 防并发覆写）';


-- -----------------------------------------------------------------------------
-- 5. t_agent_tool — Agent 与 MCP 工具的绑定关系
--    一个 Agent 可绑定多个 MCP 服务下的多个工具
--    tool_name 为空串表示启用该 MCP 服务的全部工具
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_agent_tool (
    id            BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    agent_id      BIGINT       NOT NULL                              COMMENT '关联 t_agent.id',
    mcp_server_id BIGINT       NOT NULL                              COMMENT '关联 t_mcp_server.id',
    tool_name     VARCHAR(200) NOT NULL DEFAULT ''                   COMMENT '工具名称；空串=启用该服务全部工具',
    enabled       TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NOT NULL DEFAULT 0,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 按 Agent 查其绑定工具（最高频查询）
    INDEX idx_agent_deleted (agent_id, deleted),
    -- 防止同一 Agent 重复绑定同一工具（逻辑删除记录不参与唯一约束）
    -- 应用层在插入前检查 deleted=0 的记录是否已存在
    INDEX idx_agent_server_tool (agent_id, mcp_server_id, tool_name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 与 MCP 工具绑定关系';


-- -----------------------------------------------------------------------------
-- 6. t_chat_session — 对话会话
--    每次与 Agent 的对话对应一个 Session
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_chat_session (
    id              BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    title           VARCHAR(200) NOT NULL DEFAULT '新对话'             COMMENT '会话标题，通常取首条消息前 20 字',
    agent_id        BIGINT       NOT NULL                              COMMENT '关联 t_agent.id',
    user_id         BIGINT       NOT NULL DEFAULT 0                    COMMENT '用户 ID，Auth 模块接入前默认 0',
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'             COMMENT '状态：ACTIVE / ARCHIVED',
    message_count   INT          NOT NULL DEFAULT 0                    COMMENT '消息条数（冗余计数，避免 COUNT(*) 扫表）',
    last_message_at DATETIME(3)  NULL                                  COMMENT '最后一条消息时间，用于列表排序',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 用户会话列表，按最新消息时间倒排
    INDEX idx_user_deleted_last (user_id, deleted, last_message_at),
    -- 按 Agent 查会话（管理视图）
    INDEX idx_agent_deleted (agent_id, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话会话';


-- -----------------------------------------------------------------------------
-- 7. t_chat_message — 对话消息
--    潜在大表（单会话消息量大），禁止 LIMIT offset 深分页，使用游标分页。
--    索引设计：(session_id, deleted, created_at) 覆盖游标查询。
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_chat_message (
    id              BIGINT       NOT NULL AUTO_INCREMENT               COMMENT '主键',
    session_id      BIGINT       NOT NULL                              COMMENT '关联 t_chat_session.id',
    role            VARCHAR(20)  NOT NULL                              COMMENT '角色：USER / ASSISTANT / SYSTEM',
    content         MEDIUMTEXT   NOT NULL                              COMMENT '消息内容，支持长文本',
    model_config_id BIGINT       NULL                                  COMMENT '生成本条消息使用的模型，用户消息为 NULL',
    token_count     INT          NOT NULL DEFAULT 0                    COMMENT '本条消息消耗的 token 数',
    finish_reason   VARCHAR(50)  NOT NULL DEFAULT ''                   COMMENT '结束原因：stop / length / tool_calls / error 等',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by      BIGINT       NOT NULL DEFAULT 0,
    deleted         TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    -- 游标分页核心索引：WHERE session_id=? AND deleted=0 AND created_at < ? ORDER BY created_at DESC
    INDEX idx_session_deleted_created (session_id, deleted, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '对话消息（大表，强制游标分页，禁止 LIMIT offset）';
