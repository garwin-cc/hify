package com.hify.agent.api;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentApiKeyCreateResp extends AgentApiKeyResp {

    private String apiKey;
}
