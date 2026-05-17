package com.hify.common.task;

public interface TaskQueue {

    TaskHandle submit(TaskRequest request);

    default TaskQueueStatus status() {
        return TaskQueueStatus.unknown();
    }
}
