package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WorkflowTriggerReq {

    @NotBlank(message = "触发器类型不能为空")
    private String triggerType;

    private Integer versionNo;

    private String cronExpression;

    private LocalDateTime nextFireAt;

    private Integer enabled;
}
