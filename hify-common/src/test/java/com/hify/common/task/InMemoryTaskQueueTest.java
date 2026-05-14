package com.hify.common.task;

import org.junit.jupiter.api.Test;

import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryTaskQueueTest {

    @Test
    void submitsTaskWithTraceAwareHandle() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        ThreadPoolExecutor executor = TaskExecutors.fixed("test-task-%d", 1, 1);
        InMemoryTaskQueue queue = new InMemoryTaskQueue(executor, 1);

        TaskHandle handle = queue.submit(TaskRequest.builder()
                .taskType(TaskType.AUDIT_LOG)
                .taskId("audit-1")
                .task(() -> executed.set(true))
                .build());

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(handle.getTaskId()).isEqualTo("audit-1");
        assertThat(executed).isTrue();
    }

    @Test
    void rejectsWhenQueueIsFull() {
        ThreadPoolExecutor executor = TaskExecutors.fixed("test-task-%d", 1, 1);
        InMemoryTaskQueue queue = new InMemoryTaskQueue(executor, 0);

        assertThatThrownBy(() -> queue.submit(TaskRequest.builder()
                .taskType(TaskType.WORKFLOW_RUN)
                .taskId("workflow-1")
                .task(() -> {
                })
                .build()))
                .isInstanceOf(TaskRejectedException.class);
        executor.shutdownNow();
    }
}
