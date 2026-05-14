package com.hify.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentApiKeyReq {

    @NotBlank
    @Size(max = 100)
    private String name;
}
