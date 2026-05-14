package com.hify.agent.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentResourceRef {

    private Long id;
    private Long workspaceId;
    private Long projectId;
    private Integer enabled;
}
