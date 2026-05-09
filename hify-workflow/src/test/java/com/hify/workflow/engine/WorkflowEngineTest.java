package com.hify.workflow.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.workflow.domain.WorkflowEdgePo;
import com.hify.workflow.domain.WorkflowNodePo;
import com.hify.workflow.domain.WorkflowNodeRunPo;
import com.hify.workflow.domain.WorkflowRunPo;
import com.hify.workflow.domain.config.NodeConfigParser;
import com.hify.workflow.engine.executor.ConditionNodeConfig;
import com.hify.workflow.engine.executor.LlmNodeConfig;
import com.hify.workflow.engine.executor.NodeExecutor;
import com.hify.workflow.engine.executor.NodeExecutorRegistry;
import com.hify.workflow.infra.WorkflowEdgeMapper;
import com.hify.workflow.infra.WorkflowNodeMapper;
import com.hify.workflow.infra.WorkflowNodeRunMapper;
import com.hify.workflow.infra.WorkflowRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowEngineTest {

    private WorkflowNodeMapper nodeMapper;
    private WorkflowEdgeMapper edgeMapper;
    private WorkflowRunMapper runMapper;
    private WorkflowNodeRunMapper nodeRunMapper;
    private WorkflowEngine engine;
    private List<WorkflowRunPo> updatedRuns;
    private List<WorkflowNodeRunPo> updatedNodeRuns;

    @BeforeEach
    void setUp() {
        AtomicLong ids = new AtomicLong(1);
        updatedRuns = new ArrayList<>();
        updatedNodeRuns = new ArrayList<>();
        runMapper = mapper(WorkflowRunMapper.class, method -> {
            if ("insert".equals(method)) {
                return args -> {
                    ((WorkflowRunPo) args[0]).setId(ids.getAndIncrement());
                    return 1;
                };
            }
            if ("updateById".equals(method)) {
                return args -> {
                    updatedRuns.add((WorkflowRunPo) args[0]);
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
        engine = new WorkflowEngine(
                nodeMapper,
                edgeMapper,
                new NodeConfigParser(new ObjectMapper()),
                new NodeExecutorRegistry(List.of(new StubLlmExecutor(), new StubConditionExecutor())),
                runMapper,
                nodeRunMapper,
                new ObjectMapper());
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
                new ObjectMapper());

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
                new ObjectMapper());

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
                new ObjectMapper());

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
            throw new BizException(com.hify.common.exception.ErrorCode.WORKFLOW_EXECUTE_FAILED, "boom");
        }

        @Override
        public String nodeType() {
            return "LLM";
        }
    }
}
