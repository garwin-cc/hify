package com.hify.model.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_model_default_policy")
@EqualsAndHashCode(callSuper = false)
public class ModelDefaultPolicyPo extends BaseEntity {

    private String scopeType;
    private Long scopeId;
    private String modelType;
    private String providerType;
    private Long modelConfigId;
    private Integer enabled;
}
