package com.hify.model.infra;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 供应商健康状态，每个 provider 一行，通过 UPSERT 原地更新。
 * 不继承 BaseEntity：无 createdAt / createdBy / deleted，高频写入不污染主表缓存。
 */
@Data
@TableName("t_provider_health")
public class ProviderHealthPo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;

    /** UP / DOWN / DEGRADED / UNKNOWN */
    private String status;

    private LocalDateTime lastCheckAt;

    private LocalDateTime lastSuccessAt;

    /** 当前连续失败次数，成功后清零 */
    private Integer failCount;

    /** 最近一次探活耗时（ms） */
    private Integer latencyMs;

    /** 最近一次失败原因，成功时为空串 */
    private String errorMessage;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
