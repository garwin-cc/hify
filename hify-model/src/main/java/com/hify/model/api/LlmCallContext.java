package com.hify.model.api;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmCallContext {

    private Long userId;
    private Long projectId;
    private Long appId;
    private Long agentId;
}
