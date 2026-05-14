package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_workflow_publish")
@EqualsAndHashCode(callSuper = false)
public class WorkflowPublishPo extends BaseEntity {

    private Long workflowId;

    private Long workflowVersionId;

    private String publishType;

    private String publishStatus;

    private String endpointKey;

    private String toolKey;

    private String displayName;

    private Integer grayPercent;

    private Long publishedBy;

    private LocalDateTime publishedAt;
}
