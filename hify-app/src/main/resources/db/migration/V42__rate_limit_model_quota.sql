INSERT INTO t_rate_limit_quota
(scope_type, scope_id, dimension, quota_key, limit_count, window_sec, fail_open, enabled, description, created_by, deleted)
SELECT 'GLOBAL', 0, 'MODEL', '*', 600, 60, 1, 1, '默认模型调用频率', 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM t_rate_limit_quota
    WHERE scope_type = 'GLOBAL'
      AND scope_id = 0
      AND dimension = 'MODEL'
      AND quota_key = '*'
      AND deleted = 0
);
