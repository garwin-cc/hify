package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_node")
@EqualsAndHashCode(callSuper = false)
public class WorkflowNodePo extends BaseEntity {

    private Long workflowId;

    private String nodeKey;

    private String nodeType;

    private String name;

    private String config;

    private Integer positionX;

    private Integer positionY;
}
