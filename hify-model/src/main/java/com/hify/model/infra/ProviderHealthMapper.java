package com.hify.model.infra;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProviderHealthMapper extends BaseMapper<ProviderHealthPo> {

    @Select("SELECT * FROM t_provider_health WHERE provider_id = #{providerId}")
    ProviderHealthPo selectByProviderId(@Param("providerId") Long providerId);

    /**
     * 写入或更新健康状态（INSERT ... ON DUPLICATE KEY UPDATE）。
     * 依赖 t_provider_health.provider_id 上的唯一索引，避免并发探活时重复插入。
     */
    @Insert("""
            INSERT INTO t_provider_health
                (provider_id, status, last_check_at, last_success_at,
                 fail_count, latency_ms, error_message, updated_at)
            VALUES
                (#{h.providerId}, #{h.status}, #{h.lastCheckAt}, #{h.lastSuccessAt},
                 #{h.failCount}, #{h.latencyMs}, #{h.errorMessage}, #{h.updatedAt})
            ON DUPLICATE KEY UPDATE
                status          = VALUES(status),
                last_check_at   = VALUES(last_check_at),
                last_success_at = VALUES(last_success_at),
                fail_count      = VALUES(fail_count),
                latency_ms      = VALUES(latency_ms),
                error_message   = VALUES(error_message),
                updated_at      = VALUES(updated_at)
            """)
    int upsert(@Param("h") ProviderHealthPo health);
}
