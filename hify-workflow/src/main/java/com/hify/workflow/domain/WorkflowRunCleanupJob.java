package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.workflow.infra.WorkflowRunMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowRunCleanupJob {

    private final WorkflowRunMapper workflowRunMapper;
    private final WorkflowRunEventService workflowRunEventService;

    @EventListener(ApplicationReadyEvent.class)
    public void markStaleRunningRunsFailed() {
        try {
            List<WorkflowRunPo> staleRuns = workflowRunMapper.selectList(
                    Wrappers.lambdaQuery(WorkflowRunPo.class)
                            .eq(WorkflowRunPo::getStatus, "RUNNING"));
            for (WorkflowRunPo run : staleRuns) {
                run.setStatus("FAILED");
                run.setError("服务重启导致异步工作流中断，请重新执行");
                run.setFinishedAt(LocalDateTime.now());
                workflowRunMapper.updateById(run);
                workflowRunEventService.publishRunEvent(run.getId(), "RUN_FAILED", "FAILED",
                        java.util.Map.of("error", run.getError()));
            }
            if (!staleRuns.isEmpty()) {
                log.warn("marked stale workflow runs failed count={}", staleRuns.size());
            }
        } catch (Exception e) {
            log.warn("skip stale workflow run cleanup: {}", e.getMessage());
        }
    }
}
