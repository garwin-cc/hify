package com.hify.agent.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@TableName("t_agent_app")
@EqualsAndHashCode(callSuper = false)
public class AgentAppPo extends BaseEntity {

    private Long agentId;
    private Long publishedVersionId;
    private String name;
    private String description;
    private Integer webEnabled;
    private Integer apiEnabled;
    private String endpointPath;
    private String status;
}
