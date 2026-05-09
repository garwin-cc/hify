package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_edge")
@EqualsAndHashCode(callSuper = false)
public class WorkflowEdgePo extends BaseEntity {

    private Long workflowId;

    private String sourceNodeKey;

    private String targetNodeKey;

    private String edgeType;

    private String conditionExpression;

    private Integer sortOrder;
}
