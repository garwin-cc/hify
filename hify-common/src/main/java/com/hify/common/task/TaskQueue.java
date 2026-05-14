package com.hify.common.task;

public interface TaskQueue {

    TaskHandle submit(TaskRequest request);
}
