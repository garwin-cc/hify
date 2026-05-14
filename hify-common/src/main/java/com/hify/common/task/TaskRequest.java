package com.hify.common.task;

import com.hify.common.log.TraceSnapshot;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;

@Data
@Builder
public class TaskRequest {

    private TaskType taskType;
    private String taskId;
    @Builder.Default
    private TaskPriority priority = TaskPriority.NORMAL;
    private Duration timeout;
    private TraceSnapshot traceSnapshot;
    private Runnable task;
}
