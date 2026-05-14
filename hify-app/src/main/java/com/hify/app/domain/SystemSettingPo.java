package com.hify.app.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_system_setting")
@EqualsAndHashCode(callSuper = false)
public class SystemSettingPo extends BaseEntity {

    private String settingKey;
    private String settingValue;
    private String description;
}
