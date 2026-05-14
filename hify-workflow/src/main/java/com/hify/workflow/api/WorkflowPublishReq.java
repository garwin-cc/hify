package com.hify.workflow.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WorkflowPublishReq {

    private Integer versionNo;

    @NotBlank(message = "发布类型不能为空")
    private String publishType;

    private String displayName;

    private Integer grayPercent;
}
