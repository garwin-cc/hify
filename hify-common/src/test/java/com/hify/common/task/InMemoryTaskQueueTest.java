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

    @Test
    void should_reportQueueStatus_when_tasksAreSubmittedAndRejected() throws Exception {
        AtomicBoolean executed = new AtomicBoolean(false);
        ThreadPoolExecutor executor = TaskExecutors.fixed("test-task-%d", 1, 1);
        InMemoryTaskQueue queue = new InMemoryTaskQueue("knowledge", executor, 1);

        queue.submit(TaskRequest.builder()
                .taskType(TaskType.KNOWLEDGE_PROCESS)
                .taskId("doc-1")
                .task(() -> executed.set(true))
                .build());

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        TaskQueueStatus status = queue.status();
        assertThat(status.name()).isEqualTo("knowledge");
        assertThat(status.maxPending()).isEqualTo(1);
        assertThat(status.submittedCount()).isEqualTo(1);
        assertThat(status.completedCount()).isEqualTo(1);
        assertThat(status.rejectedCount()).isZero();
        assertThat(status.saturated()).isFalse();
    }

    @Test
    void should_incrementRejectedCount_when_queueIsFull() {
        ThreadPoolExecutor executor = TaskExecutors.fixed("test-task-%d", 1, 1);
        InMemoryTaskQueue queue = new InMemoryTaskQueue("workflow", executor, 0);

        assertThatThrownBy(() -> queue.submit(TaskRequest.builder()
                .taskType(TaskType.WORKFLOW_RUN)
                .taskId("workflow-2")
                .task(() -> {
                })
                .build()))
                .isInstanceOf(TaskRejectedException.class);

        assertThat(queue.status().rejectedCount()).isEqualTo(1);
        assertThat(queue.status().saturated()).isTrue();
        executor.shutdownNow();
    }
}
