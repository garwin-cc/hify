package com.hify.auth.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workspace")
@EqualsAndHashCode(callSuper = false)
public class WorkspacePo extends BaseEntity {

    private String name;

    private String code;

    private String status;
}
