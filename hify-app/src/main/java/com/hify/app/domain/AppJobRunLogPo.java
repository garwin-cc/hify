package com.hify.app.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hify.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@TableName("t_app_job_run_log")
@EqualsAndHashCode(callSuper = false)
public class AppJobRunLogPo extends BaseEntity {

    private String jobName;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long elapsedMs;
    private Integer affectedRows;
    private String errorSummary;
}
