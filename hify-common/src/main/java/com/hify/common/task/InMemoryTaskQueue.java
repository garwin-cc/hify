package com.hify.common.task;

import com.hify.common.log.TraceContext;
import com.hify.common.log.TraceSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@RequiredArgsConstructor
public class InMemoryTaskQueue implements TaskQueue {

    private final ThreadPoolExecutor executor;
    private final int maxPending;

    @Override
    public TaskHandle submit(TaskRequest request) {
        if (request == null || request.getTask() == null || request.getTaskType() == null) {
            throw new TaskRejectedException("任务参数不完整");
        }
        if (pendingCount() >= maxPending) {
            throw new TaskRejectedException("任务队列已满");
        }
        String taskId = request.getTaskId() == null || request.getTaskId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getTaskId();
        TraceSnapshot snapshot = request.getTraceSnapshot() == null ? TraceContext.snapshot() : request.getTraceSnapshot();
        try {
            executor.execute(TraceContext.wrap(snapshot, () -> runTask(taskId, request)));
            return new TaskHandle(taskId, request.getTaskType(), TaskStatus.SUBMITTED);
        } catch (RejectedExecutionException e) {
            throw new TaskRejectedException("任务队列已满");
        }
    }

    private void runTask(String taskId, TaskRequest request) {
        long start = System.currentTimeMillis();
        TraceContext.put("taskId", taskId);
        TraceContext.put("taskType", request.getTaskType());
        try {
            request.getTask().run();
            log.info("task finished type={} id={} elapsedMs={}",
                    request.getTaskType(), taskId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("task failed type={} id={} elapsedMs={} message={}",
                    request.getTaskType(), taskId, System.currentTimeMillis() - start, e.getMessage(), e);
            throw e;
        }
    }

    private int pendingCount() {
        return executor.getQueue().size() + executor.getActiveCount();
    }
}
