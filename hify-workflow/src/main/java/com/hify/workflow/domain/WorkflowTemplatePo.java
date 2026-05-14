package com.hify.workflow.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_workflow_template")
@EqualsAndHashCode(callSuper = false)
public class WorkflowTemplatePo extends BaseEntity {

    private Long workspaceId;

    private Long projectId;

    private String name;

    private String description;

    private String category;

    private String icon;

    private String configJson;

    private Integer enabled;

    private Integer builtin;

    private String status;

    private Long currentVersionId;

    private Integer latestVersionNo;

    private String tagsJson;

    private Integer nodeCount;

    private String nodeTypesJson;

    private Integer requirementCount;

    private Integer usageCount;

    private java.time.LocalDateTime lastUsedAt;

    private Long createdFromWorkflowId;

    private java.time.LocalDateTime publishedAt;

    private java.time.LocalDateTime archivedAt;
}
