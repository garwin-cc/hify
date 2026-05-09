DROP TABLE IF EXISTS t_agent_tool;

CREATE TABLE t_agent_tool (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    agent_id   BIGINT      NOT NULL,
    tool_id    BIGINT      NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_agent_tool (agent_id, tool_id),
    INDEX idx_tool_id (tool_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Agent 与 MCP Tool 绑定关系';
