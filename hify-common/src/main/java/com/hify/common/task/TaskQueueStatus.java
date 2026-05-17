package com.hify.common.task;

public record TaskQueueStatus(
        String name,
        int activeCount,
        int queuedCount,
        int remainingCapacity,
        int maxPending,
        long submittedCount,
        long completedCount,
        long failedCount,
        long rejectedCount,
        boolean saturated
) {

    static TaskQueueStatus unknown() {
        return new TaskQueueStatus("unknown", 0, 0, 0, 0, 0, 0, 0, 0, false);
    }
}
