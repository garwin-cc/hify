package com.hify.common.task;

import com.hify.common.log.TraceContext;
import com.hify.common.log.TraceSnapshot;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class InMemoryTaskQueue implements TaskQueue {

    private final String name;
    private final ThreadPoolExecutor executor;
    private final int maxPending;
    private final AtomicLong submittedCount = new AtomicLong();
    private final AtomicLong completedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();
    private final AtomicLong rejectedCount = new AtomicLong();

    public InMemoryTaskQueue(ThreadPoolExecutor executor, int maxPending) {
        this("default", executor, maxPending);
    }

    public InMemoryTaskQueue(String name, ThreadPoolExecutor executor, int maxPending) {
        this.name = name == null || name.isBlank() ? "default" : name;
        this.executor = executor;
        this.maxPending = maxPending;
    }

    @Override
    public TaskHandle submit(TaskRequest request) {
        if (request == null || request.getTask() == null || request.getTaskType() == null) {
            throw new TaskRejectedException("任务参数不完整");
        }
        if (pendingCount() >= maxPending) {
            rejectedCount.incrementAndGet();
            throw new TaskRejectedException("任务队列已满");
        }
        String taskId = request.getTaskId() == null || request.getTaskId().isBlank()
                ? UUID.randomUUID().toString()
                : request.getTaskId();
        TraceSnapshot snapshot = request.getTraceSnapshot() == null ? TraceContext.snapshot() : request.getTraceSnapshot();
        try {
            executor.execute(TraceContext.wrap(snapshot, () -> runTask(taskId, request)));
            submittedCount.incrementAndGet();
            return new TaskHandle(taskId, request.getTaskType(), TaskStatus.SUBMITTED);
        } catch (RejectedExecutionException e) {
            rejectedCount.incrementAndGet();
            throw new TaskRejectedException("任务队列已满");
        }
    }

    @Override
    public TaskQueueStatus status() {
        int queued = executor.getQueue() == null ? 0 : executor.getQueue().size();
        int remaining = executor.getQueue() == null ? Integer.MAX_VALUE : executor.getQueue().remainingCapacity();
        return new TaskQueueStatus(
                name,
                executor.getActiveCount(),
                queued,
                remaining,
                maxPending,
                submittedCount.get(),
                completedCount.get(),
                failedCount.get(),
                rejectedCount.get(),
                pendingCount() >= maxPending || remaining == 0
        );
    }

    private void runTask(String taskId, TaskRequest request) {
        long start = System.currentTimeMillis();
        TraceContext.put("taskId", taskId);
        TraceContext.put("taskType", request.getTaskType());
        try {
            request.getTask().run();
            completedCount.incrementAndGet();
            log.info("task finished type={} id={} elapsedMs={}",
                    request.getTaskType(), taskId, System.currentTimeMillis() - start);
        } catch (Exception e) {
            failedCount.incrementAndGet();
            log.warn("task failed type={} id={} elapsedMs={} message={}",
                    request.getTaskType(), taskId, System.currentTimeMillis() - start, e.getMessage(), e);
            throw e;
        }
    }

    private int pendingCount() {
        return executor.getQueue().size() + executor.getActiveCount();
    }
}
