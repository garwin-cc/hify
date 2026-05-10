package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_template")
@EqualsAndHashCode(callSuper = false)
public class WorkflowTemplatePo extends BaseEntity {

    private String name;

    private String description;

    private String category;

    private String icon;

    private String configJson;

    private Integer enabled;

    private Integer builtin;
}
