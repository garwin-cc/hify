package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_workflow_template_version")
@EqualsAndHashCode(callSuper = false)
public class WorkflowTemplateVersionPo extends BaseEntity {

    private Long templateId;

    private Integer versionNo;

    private String snapshotJson;

    private String requirementsJson;

    private Integer nodeCount;

    private String nodeTypesJson;

    private String checksum;

    private String changelog;

    private String validationStatus;

    private String validationErrorsJson;

    private Long publishedBy;

    private LocalDateTime publishedAt;
}
