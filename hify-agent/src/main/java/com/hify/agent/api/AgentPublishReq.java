package com.hify.agent.api;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentPublishReq {

    @Size(max = 500)
    private String description;
}
