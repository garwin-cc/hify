package com.hify.common.task;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TaskHandle {

    private String taskId;
    private TaskType taskType;
    private TaskStatus status;
}
