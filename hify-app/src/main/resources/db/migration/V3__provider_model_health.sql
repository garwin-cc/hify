-- =============================================================================
-- V3: Provider 模块 Schema 重构
--
-- 变更原因：
--   1. t_provider.api_key → auth_config JSON，统一存储不同供应商的鉴权差异
--   2. t_model_config 字段对齐新 Entity（rename + 增加 extra_params）
--   3. 新增 t_provider_health，独立维护探活状态（高频写，不影响 Provider 主表）
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. t_provider — 鉴权字段重构
-- -----------------------------------------------------------------------------

-- api_key 明文列替换为 JSON 结构，为后续加密和多字段扩展做准备
ALTER TABLE t_provider
    DROP COLUMN api_key,
    ADD COLUMN auth_config JSON NULL COMMENT '鉴权配置 JSON，结构随 type 不同；OLLAMA 时为 null' AFTER base_url,
    ADD COLUMN sort_order  INT  NOT NULL DEFAULT 0 COMMENT '列表排序权重，数值越小越靠前'   AFTER enabled;


-- -----------------------------------------------------------------------------
-- 2. t_model_config — 字段对齐
-- -----------------------------------------------------------------------------

-- display_name → name（命名统一，与其他表保持一致）
ALTER TABLE t_model_config
    RENAME COLUMN display_name   TO name,
    RENAME COLUMN context_window TO context_size;

-- max_tokens 移入 extra_params，模型层不再硬存（由调用方按需覆盖）
ALTER TABLE t_model_config
    DROP COLUMN max_tokens,
    ADD COLUMN extra_params JSON NULL COMMENT '模型级别扩展参数，透传给 API，覆盖 Provider 默认值' AFTER context_size,
    ADD COLUMN sort_order   INT  NOT NULL DEFAULT 0 COMMENT '同一 Provider 下的模型排序权重' AFTER enabled;

-- model_id 在同一 Provider 下不允许重复（deleted=0 的记录）
-- 注：逻辑删除记录不参与唯一约束，应用层在插入前检查 deleted=0 的行是否已存在
ALTER TABLE t_model_config
    ADD UNIQUE INDEX uk_provider_model (provider_id, model_id);


-- -----------------------------------------------------------------------------
-- 3. t_provider_health — 新建探活状态表
-- -----------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS t_provider_health (
    id              BIGINT       NOT NULL AUTO_INCREMENT                    COMMENT '主键',
    provider_id     BIGINT       NOT NULL                                   COMMENT '关联 t_provider.id',
    status          VARCHAR(20)  NOT NULL DEFAULT 'UNKNOWN'                 COMMENT 'UP / DOWN / DEGRADED / UNKNOWN',
    last_check_at   DATETIME(3)  NULL                                       COMMENT '最近一次探活时间',
    last_success_at DATETIME(3)  NULL                                       COMMENT '最近一次探活成功时间',
    fail_count      INT          NOT NULL DEFAULT 0                         COMMENT '当前连续失败次数，成功后清零',
    latency_ms      INT          NULL                                       COMMENT '最近一次探活耗时（ms）',
    error_message   VARCHAR(500) NOT NULL DEFAULT ''                        COMMENT '最近一次失败原因，成功后清空',
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                          ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    -- provider_id 全局唯一：每个 Provider 仅一行，UPSERT 依赖此索引
    UNIQUE INDEX uk_provider_id (provider_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '供应商探活状态（独立表，高频写不影响 t_provider 缓存）';
