package com.hify.agent.api;

import lombok.Data;

@Data
public class AgentQuery {

    private String name;

    /** 1=启用，0=禁用，null=全部 */
    private Integer enabled;

    private Long projectId;

    private int page = 1;

    private int pageSize = 20;
}
