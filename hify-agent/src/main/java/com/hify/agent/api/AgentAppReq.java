package com.hify.agent.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AgentAppReq {

    @NotNull
    private Long publishedVersionId;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Size(max = 500)
    private String description;

    private Integer webEnabled;

    private Integer apiEnabled;

    @Size(max = 128)
    private String endpointPath;
}
