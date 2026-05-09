-- V12: Agent 绑定工作流

ALTER TABLE t_agent
    ADD COLUMN workflow_id BIGINT NULL COMMENT '绑定的工作流 ID'
        AFTER model_config_id;
