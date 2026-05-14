package com.hify.app.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_rate_limit_quota")
@EqualsAndHashCode(callSuper = false)
public class RateLimitQuotaPo extends BaseEntity {

    private String scopeType;
    private Long scopeId;
    private String dimension;
    private String quotaKey;
    private Integer limitCount;
    private Integer windowSec;
    private Integer failOpen;
    private Integer enabled;
    private String description;
}
