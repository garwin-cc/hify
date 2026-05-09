CREATE TABLE IF NOT EXISTS t_mcp_tool (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    mcp_server_id BIGINT       NOT NULL                COMMENT '关联 t_mcp_server.id',
    name          VARCHAR(200) NOT NULL                COMMENT '工具名称',
    description   VARCHAR(1000) NOT NULL DEFAULT ''    COMMENT '工具描述',
    input_schema  JSON         NULL                    COMMENT '工具入参 JSON Schema',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by    BIGINT       NOT NULL DEFAULT 0,
    deleted       TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_server_deleted (mcp_server_id, deleted),
    INDEX idx_server_name_deleted (mcp_server_id, name, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'MCP Server 工具清单';
