package com.hify.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.http.LlmApiException;
import com.hify.workflow.domain.WorkflowEdgePo;
import com.hify.workflow.domain.WorkflowEventPublisher;
import com.hify.workflow.domain.WorkflowNodePo;
import com.hify.workflow.domain.WorkflowNodeRunPo;
import com.hify.workflow.domain.WorkflowRunPo;
import com.hify.workflow.domain.WorkflowReviewHandler;
import com.hify.workflow.domain.config.NodeConfigParser;
import com.hify.workflow.engine.executor.ConditionNodeConfig;
import com.hify.workflow.engine.executor.LlmNodeConfig;
import com.hify.workflow.engine.executor.NodeExecutor;
import com.hify.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineTest {

    private WorkflowNodeMapper nodeMapper;
    private WorkflowEdgeMapper edgeMapper;
    private WorkflowRunMapper runMapper;
    private WorkflowNodeRunMapper nodeRunMapper;
    private WorkflowVersionMapper versionMapper;
    private WorkflowEngine engine;
    private List<WorkflowRunPo> updatedRuns;
    private List<WorkflowNodeRunPo> updatedNodeRuns;
    private RecordingWorkflowReviewHandler reviewHandler;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(1);
        updatedRuns = new ArrayList<>();
        updatedNodeRuns = new ArrayList<>();
        reviewHandler = new RecordingWorkflowReviewHandler();
        runMapper = mapper(WorkflowRunMapper.class, method -> {
            if ("insert".equals(method)) {
                return args -> {
                    ((WorkflowRunPo) args[0]).setId(ids.getAndIncrement());
                    return 1;
                };
            }
            if ("updateById".equals(method)) {
                return args -> {
                    updatedRuns.add(copyRun((WorkflowRunPo) args[0]));
                    return 1;
                };
            }
            return null;
        });
        nodeRunMapper = mapper(WorkflowNodeRunMapper.class, method -> {
            if ("insert".equals(method)) {
                return args -> {
                    ((WorkflowNodeRunPo) args[0]).setId(ids.getAndIncrement());
                    return 1;
                };
            }
            if ("updateById".equals(method)) {
                return args -> {
                    updatedNodeRuns.add((WorkflowNodeRunPo) args[0]);
                    return 1;
                };
            }
            return null;
        });
        versionMapper = mapper(WorkflowVersionMapper.class, method -> null);
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);
    }

    @Test
    void executesLinearWorkflowWithUnconditionalEdges() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"llm.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        String output = engine.execute(10L, "hello");

        assertThat(output).isEqualTo("answer: hello");
    }

    @Test
    void executesConditionalBranchByBooleanResult() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("condition", "CONDITION", "{\"outputVariable\":\"ok\"}"),
                node("yes", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("no", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"yes.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "condition", null, 0),
                edge("condition", "yes", "true", 0),
                edge("condition", "no", "false", 1),
                edge("yes", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        String output = engine.execute(10L, "match");

        assertThat(output).isEqualTo("answer: match");
    }

    @Test
    void marksNodeAndWorkflowFailedWhenExecutorThrows() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"llm.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new FailingLlmExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        assertThatThrownBy(() -> engine.execute(10L, "hello"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("boom");
        assertThat(updatedNodeRuns).anySatisfy(run -> {
            assertThat(run.getNodeKey()).isEqualTo("llm");
            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(run.getError()).contains("boom");
        });
        assertThat(updatedRuns).anySatisfy(run -> {
            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(run.getError()).contains("boom");
        });
    }

    @Test
    void marksWorkflowTimeoutWhenLlmCallTimesOut() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"llm.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new TimeoutLlmExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        assertThatThrownBy(() -> engine.execute(10L, "hello"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("LLM 请求超时");
        assertThat(updatedNodeRuns).anySatisfy(run -> {
            assertThat(run.getNodeKey()).isEqualTo("llm");
            assertThat(run.getStatus()).isEqualTo("FAILED");
            assertThat(run.getError()).contains("LLM 请求超时");
        });
        assertThat(updatedRuns).anySatisfy(run -> {
            assertThat(run.getStatus()).isEqualTo("TIMEOUT");
            assertThat(run.getError()).contains("LLM 请求超时");
        });
    }

    @Test
    void waitsAtHumanReviewAndPersistsContextSnapshot() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("review", "HUMAN_REVIEW", "{\"title\":\"确认\",\"content\":\"确认 {{llm.answer}}\",\"outputVariable\":\"result\"}"),
                node("end", "END", "{\"outputVariable\":\"llm.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "review", null, 0),
                edge("review", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        String output = engine.execute(10L, "hello");

        assertThat(output).isNull();
        assertThat(reviewHandler.created).isTrue();
        assertThat(reviewHandler.content).isEqualTo("确认 answer: hello");
        assertThat(updatedRuns).anySatisfy(run -> {
            assertThat(run.getStatus()).isEqualTo("WAITING");
            assertThat(run.getCurrentNodeKey()).isEqualTo("review");
            assertThat(run.getContextSnapshot()).contains("llm.answer");
        });
        assertThat(updatedNodeRuns).anySatisfy(run -> {
            assertThat(run.getNodeKey()).isEqualTo("review");
            assertThat(run.getStatus()).isEqualTo("WAITING");
        });
    }

    @Test
    void resumesAfterApprovedHumanReviewFromNextNode() {
        WorkflowRunPo existingRun = new WorkflowRunPo();
        existingRun.setId(99L);
        existingRun.setWorkflowId(10L);
        existingRun.setStatus("RUNNING");
        existingRun.setInput("hello");
        existingRun.setCurrentNodeKey("review");
        existingRun.setContextSnapshot("{\"start.userMessage\":\"hello\",\"llm.answer\":\"draft\",\"review.result\":{\"action\":\"APPROVE\"}}");
        runMapper = mapper(WorkflowRunMapper.class, method -> {
            if ("selectById".equals(method)) {
                return args -> existingRun;
            }
            if ("updateById".equals(method)) {
                return args -> {
                    updatedRuns.add(copyRun((WorkflowRunPo) args[0]));
                    return 1;
                };
            }
            return null;
        });
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("review", "HUMAN_REVIEW", "{\"title\":\"确认\",\"content\":\"{{llm.answer}}\",\"outputVariable\":\"result\"}"),
                node("final", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"final.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "review", null, 0),
                edge("review", "final", "APPROVE", 0),
                edge("final", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        String output = engine.resumeAfterReview(99L);

        assertThat(output).isEqualTo("answer: hello");
        assertThat(updatedRuns).anySatisfy(run -> {
            assertThat(run.getStatus()).isEqualTo("SUCCESS");
            assertThat(run.getOutput()).isEqualTo("answer: hello");
        });
    }


    @Test
    void executesExistingRunWithoutCreatingAnotherRunAndTracksCurrentNode() {
        WorkflowRunPo existingRun = new WorkflowRunPo();
        existingRun.setId(99L);
        existingRun.setWorkflowId(10L);
        existingRun.setStatus("RUNNING");
        existingRun.setInput("hello");
        runMapper = mapper(WorkflowRunMapper.class, method -> {
            if ("selectById".equals(method)) {
                return args -> existingRun;
            }
            if ("insert".equals(method)) {
                return args -> {
                    throw new AssertionError("should not create a new workflow run");
                };
            }
            if ("updateById".equals(method)) {
                return args -> {
                    updatedRuns.add(copyRun((WorkflowRunPo) args[0]));
                    return 1;
                };
            }
            return null;
        });
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"llm.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        String output = engine.executeExistingRun(99L, 10L, "hello");

        assertThat(output).isEqualTo("answer: hello");
        assertThat(updatedRuns).anySatisfy(run -> {
            assertThat(run.getId()).isEqualTo(99L);
            assertThat(run.getCurrentNodeKey()).isEqualTo("llm");
        });
        assertThat(updatedRuns).anySatisfy(run -> {
            assertThat(run.getId()).isEqualTo(99L);
            assertThat(run.getStatus()).isEqualTo("SUCCESS");
            assertThat(run.getOutput()).isEqualTo("answer: hello");
        });
    }

    @Test
    void recordsTraceIdAndNodeInputSnapshotForRunTroubleshooting() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"llm.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        engine.execute(10L, "hello");

        assertThat(updatedRuns).anySatisfy(run -> assertThat(run.getTraceId()).isNotBlank());
        assertThat(updatedNodeRuns).anySatisfy(run -> {
            assertThat(run.getNodeKey()).isEqualTo("llm");
            assertThat(run.getStartedAt()).isNotNull();
            assertThat(run.getInputSnapshot()).contains("\"start.userMessage\":\"hello\"");
            assertThat(run.getOutputs()).contains("\"llm.answer\":\"answer: hello\"");
        });
    }

    @Test
    void retriesNodeWhenRuntimePolicyAllowsRetry() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\",\"runtime\":{\"retryTimes\":1}}"),
                node("end", "END", "{\"outputVariable\":\"llm.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "end", null, 0)
        ));
        FlakyLlmExecutor flakyExecutor = new FlakyLlmExecutor();
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(flakyExecutor, new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        String output = engine.execute(10L, "hello");

        assertThat(output).isEqualTo("answer: hello");
        assertThat(flakyExecutor.calls).isEqualTo(2);
        assertThat(updatedNodeRuns).anySatisfy(run -> {
            assertThat(run.getNodeKey()).isEqualTo("llm");
            assertThat(run.getMaxAttempts()).isEqualTo(2);
            assertThat(run.getAttemptNo()).isEqualTo(2);
        });
    }

    @Test
    void routesToErrorBranchWhenRuntimePolicyUsesErrorBranch() {
        nodeMapper = selectListMapper(WorkflowNodeMapper.class, List.of(
                node("start", "START", "{}"),
                node("llm", "LLM", "{\"outputVariable\":\"answer\",\"runtime\":{\"onFailure\":\"ERROR_BRANCH\"}}"),
                node("fallback", "LLM", "{\"outputVariable\":\"answer\"}"),
                node("end", "END", "{\"outputVariable\":\"fallback.answer\"}")
        ));
        edgeMapper = selectListMapper(WorkflowEdgeMapper.class, List.of(
                edge("start", "llm", null, 0),
                edge("llm", "fallback", "ERROR", 0),
                edge("fallback", "end", null, 0)
        ));
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new FailingOnceThenNormalExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                versionMapper,
                new ObjectMapper(),
                new NoopWorkflowEventPublisher(),
                reviewHandler);

        String output = engine.execute(10L, "hello");

        assertThat(output).isEqualTo("answer: hello");
        assertThat(updatedRuns).anySatisfy(run -> {
            assertThat(run.getStatus()).isEqualTo("SUCCESS");
            assertThat(run.getOutput()).isEqualTo("answer: hello");
        });
    }

    private static WorkflowNodePo node(String key, String type, String config) {
        WorkflowNodePo po = new WorkflowNodePo();
        po.setWorkflowId(10L);
        po.setNodeKey(key);
        po.setNodeType(type);
        po.setName(key);
        po.setConfig(config);
        return po;
    }

    private static WorkflowEdgePo edge(String source, String target, String condition, int sortOrder) {
        WorkflowEdgePo po = new WorkflowEdgePo();
        po.setWorkflowId(10L);
        po.setSourceNodeKey(source);
        po.setTargetNodeKey(target);
        po.setConditionExpression(condition);
        po.setSortOrder(sortOrder);
        return po;
    }

    private static WorkflowRunPo copyRun(WorkflowRunPo source) {
        WorkflowRunPo copy = new WorkflowRunPo();
        copy.setId(source.getId());
        copy.setWorkflowId(source.getWorkflowId());
        copy.setTraceId(source.getTraceId());
        copy.setRerunFromRunId(source.getRerunFromRunId());
        copy.setStatus(source.getStatus());
        copy.setInput(source.getInput());
        copy.setOutput(source.getOutput());
        copy.setError(source.getError());
        copy.setCurrentNodeKey(source.getCurrentNodeKey());
        copy.setTimeoutAt(source.getTimeoutAt());
        copy.setRunMode(source.getRunMode());
        copy.setContextSnapshot(source.getContextSnapshot());
        copy.setElapsedMs(source.getElapsedMs());
        copy.setFinishedAt(source.getFinishedAt());
        return copy;
    }

    private static <T> T selectListMapper(Class<T> type, List<?> rows) {
        return mapper(type, method -> "selectList".equals(method) ? args -> rows : null);
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
                    if (method.getReturnType().isPrimitive()) {
                        return 0;
                    }
                    return null;
                }));
    }

    private interface Invocation {
        Object invoke(Object[] args);
    }

    private static class StubLlmExecutor implements NodeExecutor {

        @Override
        public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
            LlmNodeConfig llmConfig = (LlmNodeConfig) config;
            ctx.set(node.nodeKey(), llmConfig.outputVariable(), "answer: " + ctx.get("start", "userMessage"));
        }

        @Override
        public String nodeType() {
            return "LLM";
        }
    }

    private static class StubConditionExecutor implements NodeExecutor {

        @Override
        public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
            ConditionNodeConfig conditionConfig = (ConditionNodeConfig) config;
            ctx.set(node.nodeKey(), conditionConfig.outputVariable(), "match".equals(ctx.get("start", "userMessage")));
        }

        @Override
        public String nodeType() {
            return "CONDITION";
        }
    }

    private static class FailingLlmExecutor implements NodeExecutor {

        @Override
        public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, "boom");
        }

        @Override
        public String nodeType() {
            return "LLM";
        }
    }

    private static class TimeoutLlmExecutor implements NodeExecutor {

        @Override
        public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
            throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED,
                    "工作流节点执行失败: nodeKey=llm type=LLM，原因: LLM 请求超时: https://example.test",
                    new LlmApiException(LlmApiException.Type.TIMEOUT,
                            "LLM 请求超时: https://example.test"));
        }

        @Override
        public String nodeType() {
            return "LLM";
        }
    }

    private static class FlakyLlmExecutor implements NodeExecutor {

        private int calls;

        @Override
        public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
            calls++;
            if (calls == 1) {
                throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, "temporary boom");
            }
            LlmNodeConfig llmConfig = (LlmNodeConfig) config;
            ctx.set(node.nodeKey(), llmConfig.outputVariable(), "answer: " + ctx.get("start", "userMessage"));
        }

        @Override
        public String nodeType() {
            return "LLM";
        }
    }

    private static class FailingOnceThenNormalExecutor implements NodeExecutor {

        @Override
        public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
            if ("llm".equals(node.nodeKey())) {
                throw new BizException(ErrorCode.WORKFLOW_EXECUTE_FAILED, "boom");
            }
            LlmNodeConfig llmConfig = (LlmNodeConfig) config;
            ctx.set(node.nodeKey(), llmConfig.outputVariable(), "answer: " + ctx.get("start", "userMessage"));
        }

        @Override
        public String nodeType() {
            return "LLM";
        }
    }

    private static class NoopWorkflowEventPublisher implements WorkflowEventPublisher {

        @Override
        public void publishRunEvent(Long workflowRunId, String eventType, String status, Map<String, Object> payload) {
        }

        @Override
        public void publishNodeEvent(Long workflowRunId, String eventType, String nodeKey, String status, Map<String, Object> payload) {
        }
    }

    private static class RecordingWorkflowReviewHandler implements WorkflowReviewHandler {
        boolean created;
        String content;

        @Override
        public void createWaitingReview(Long workflowRunId, String nodeKey, String title, String content,
                                        List<String> actions, boolean allowEdit, String outputVariable) {
            this.created = true;
            this.content = content;
        }
    }
}
