package com.hify.agent.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_agent_api_key")
@EqualsAndHashCode(callSuper = false)
public class AgentApiKeyPo extends BaseEntity {

    private Long agentAppId;
    private String name;
    private String keyPrefix;
    private String keyHash;
    private String status;
    private LocalDateTime lastUsedAt;
}
