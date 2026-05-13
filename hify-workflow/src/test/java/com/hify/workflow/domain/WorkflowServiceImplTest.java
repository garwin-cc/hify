package com.hify.workflow.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.workflow.api.SubmitWorkflowReviewReq;
import com.hify.workflow.api.WorkflowRunResp;
import com.hify.workflow.engine.WorkflowEngine;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import com.hify.workflow.domain.config.NodeConfigParser;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowServiceImplTest {

    @Test
    void rerunsFailedWorkflowRunWithOriginalInputAndSourceRunId() {
        WorkflowRunPo oldRun = new WorkflowRunPo();
        oldRun.setId(88L);
        oldRun.setWorkflowId(10L);
        oldRun.setStatus("FAILED");
        oldRun.setInput("original input");

        WorkflowRunPo newRun = new WorkflowRunPo();
        newRun.setId(99L);
        newRun.setWorkflowId(10L);
        newRun.setStatus("RUNNING");
        newRun.setInput("original input");

        WorkflowMapper workflowMapper = mapper(WorkflowMapper.class, method -> {
            if ("selectById".equals(method)) {
                return args -> {
                    WorkflowPo workflow = new WorkflowPo();
                    workflow.setId(10L);
                    return workflow;
                };
            }
            return null;
        });
        WorkflowRunMapper runMapper = mapper(WorkflowRunMapper.class, method -> {
            if ("selectById".equals(method)) {
                return args -> Long.valueOf(88L).equals(args[0]) ? oldRun : newRun;
            }
            if ("updateById".equals(method)) {
                return args -> 1;
            }
            return null;
        });
        WorkflowNodeRunMapper nodeRunMapper = mapper(WorkflowNodeRunMapper.class, method -> {
            if ("selectList".equals(method)) {
                return args -> List.of();
            }
            return null;
        });
        RecordingWorkflowEngine workflowEngine = new RecordingWorkflowEngine(newRun);

        WorkflowServiceImpl service = new WorkflowServiceImpl(
                workflowMapper,
                mapper(WorkflowNodeMapper.class, method -> null),
                mapper(WorkflowEdgeMapper.class, method -> null),
                runMapper,
                nodeRunMapper,
                null,
                mapper(WorkflowVersionMapper.class, method -> null),
                new NodeConfigParser(new ObjectMapper()),
                workflowEngine,
                noopEventService(),
                new WorkflowReviewService(null, new ObjectMapper()),
                new ObjectMapper());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        ReflectionTestUtils.setField(service, "llmExecutor", executor);

        WorkflowRunResp response = service.rerunRun(88L);

        assertThat(response.getId()).isEqualTo(99L);
        assertThat(newRun.getRerunFromRunId()).isEqualTo(88L);
        assertThat(workflowEngine.executedRunId).isEqualTo(99L);
        assertThat(workflowEngine.executedWorkflowId).isEqualTo(10L);
        assertThat(workflowEngine.executedUserMessage).isEqualTo("original input");
        executor.shutdownNow();
    }

    @Test
    void submitReviewCancelsRunAndReviewNodeWhenActionIsReject() {
        WorkflowMapper workflowMapper = mapper(WorkflowMapper.class, method -> null);
        WorkflowNodeMapper nodeMapper = mapper(WorkflowNodeMapper.class, method -> null);
        WorkflowEdgeMapper edgeMapper = mapper(WorkflowEdgeMapper.class, method -> null);


        WorkflowRunPo run = new WorkflowRunPo();
        run.setId(88L);
        run.setWorkflowId(10L);
        run.setStatus("WAITING");
        run.setCurrentNodeKey("review");
        run.setContextSnapshot("{\"start.userMessage\":\"hello\"}");

        WorkflowReviewTaskPo task = new WorkflowReviewTaskPo();
        task.setWorkflowRunId(88L);
        task.setNodeKey("review");
        task.setOutputVariable("result");
        task.setReviewAction("REJECT");
        task.setReviewComment("需要重新修改");
        task.setReviewedAt(LocalDateTime.now());

        WorkflowNodeRunPo nodeRun = new WorkflowNodeRunPo();
        nodeRun.setId(99L);
        nodeRun.setWorkflowRunId(88L);
        nodeRun.setNodeKey("review");
        nodeRun.setNodeType("HUMAN_REVIEW");
        nodeRun.setStatus("WAITING");

        WorkflowRunMapper runMapper = mapper(WorkflowRunMapper.class, method -> {
            if ("selectById".equals(method)) {
                return args -> run;
            }
            if ("updateById".equals(method)) {
                return args -> 1;
            }
            return null;
        });
        WorkflowNodeRunMapper nodeRunMapper = mapper(WorkflowNodeRunMapper.class, method -> {
            if ("selectOne".equals(method)) {
                return args -> nodeRun;
            }
            if ("selectList".equals(method)) {
                return args -> List.of(nodeRun);
            }
            if ("updateById".equals(method)) {
                return args -> 1;
            }
            return null;
        });
        WorkflowRunEventService eventService = noopEventService();
        WorkflowReviewService reviewService = new WorkflowReviewService(null, new ObjectMapper()) {
            @Override
            public WorkflowReviewTaskPo submitReview(Long workflowRunId, SubmitWorkflowReviewReq req) {
                return task;
            }
        };

        WorkflowServiceImpl service = new WorkflowServiceImpl(
                workflowMapper,
                nodeMapper,
                edgeMapper,
                runMapper,
                nodeRunMapper,
                null,
                mapper(WorkflowVersionMapper.class, method -> null),
                new NodeConfigParser(new ObjectMapper()),
                null,
                eventService,
                reviewService,
                new ObjectMapper());
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
        ReflectionTestUtils.setField(service, "llmExecutor", executor);

        SubmitWorkflowReviewReq req = new SubmitWorkflowReviewReq();
        req.setAction("REJECT");
        req.setComment("需要重新修改");

        service.submitReview(88L, req);

        assertThat(run.getStatus()).isEqualTo("CANCELED");
        assertThat(nodeRun.getStatus()).isEqualTo("CANCELED");
        assertThat(nodeRun.getFinishedAt()).isNotNull();
    }

    private static WorkflowRunEventService noopEventService() {
        return new WorkflowRunEventService(null, new ObjectMapper()) {
            @Override
            public void publishRunEvent(Long workflowRunId, String eventType, String status, java.util.Map<String, Object> payload) {
            }

            @Override
            public void publishNodeEvent(Long workflowRunId, String eventType, String nodeKey, String status, java.util.Map<String, Object> payload) {
            }
        };
    }

    private static <T> T mapper(Class<T> type, Function<String, Invocation> behavior) {
        return type.cast(Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, args) -> {
                    Invocation invocation = behavior.apply(method.getName());
                    if (invocation != null) {
                        return invocation.invoke(args == null ? new Object[0] : args);
                    }
                    if (method.getReturnType().equals(int.class) || method.getReturnType().equals(Integer.class)) {
                        return 0;
                    }
                    if (method.getReturnType().equals(boolean.class) || method.getReturnType().equals(Boolean.class)) {
                        return false;
                    }
                    return null;
                }));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(Object[] args);
    }

    private static class RecordingWorkflowEngine extends WorkflowEngine {

        private final WorkflowRunPo newRun;
        private Long executedRunId;
        private Long executedWorkflowId;
        private String executedUserMessage;

        RecordingWorkflowEngine(WorkflowRunPo newRun) {
            super(null, null, null, null, null, null, null, null, null, null);
            this.newRun = newRun;
        }

        @Override
        public WorkflowRunPo createWorkflowRun(Long workflowId, String userMessage, String runMode, LocalDateTime timeoutAt) {
            return newRun;
        }

        @Override
        public String executeExistingRun(Long workflowRunId, Long workflowId, String userMessage) {
            this.executedRunId = workflowRunId;
            this.executedWorkflowId = workflowId;
            this.executedUserMessage = userMessage;
            return null;
        }
    }
}
