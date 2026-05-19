package com.hify.mcp.domain;

import com.hify.mcp.api.McpToolCallAuditRecord;
import com.hify.mcp.infra.McpToolCallAuditMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class McpToolCallAuditServiceImplTest {

    @Mock
    private McpToolCallAuditMapper mapper;

    @Test
    void recordPersistsOnlyArgumentKeysAndSummaries() {
        McpToolCallAuditServiceImpl service = new McpToolCallAuditServiceImpl(mapper);
        McpToolCallAuditRecord record = McpToolCallAuditRecord.builder()
                .sourceType("CONVERSATION")
                .conversationSessionId(11L)
                .conversationMessageId(22L)
                .mcpServerId(3L)
                .toolName("search")
                .arguments(Map.of("query", "secret content", "token", "sk-secret"))
                .elapsedMs(42L)
                .success(true)
                .result("abcdefghijklmnopqrstuvwxyz0123456789")
                .build();

        service.record(record);

        ArgumentCaptor<McpToolCallAuditPo> captor = ArgumentCaptor.forClass(McpToolCallAuditPo.class);
        verify(mapper).insert(captor.capture());
        McpToolCallAuditPo inserted = captor.getValue();
        assertThat(inserted.getSourceType()).isEqualTo("CONVERSATION");
        assertThat(inserted.getArgumentKeys()).containsExactly("query", "token");
        assertThat(inserted.getArgumentSummary()).doesNotContain("secret content", "sk-secret");
        assertThat(inserted.getResultSummary()).contains("abcdefghijklmnopqrstuvwxyz");
        assertThat(inserted.getSuccess()).isEqualTo(1);
        assertThat(inserted.getElapsedMs()).isEqualTo(42L);
    }

    @Test
    void recordFailureStoresControlledErrorSummary() {
        McpToolCallAuditServiceImpl service = new McpToolCallAuditServiceImpl(mapper);
        McpToolCallAuditRecord record = McpToolCallAuditRecord.builder()
                .sourceType("WORKFLOW")
                .workflowRunId(100L)
                .workflowNodeKey("code_task")
                .mcpServerId(3L)
                .toolName("code_worker")
                .arguments(Map.of("task", "change code"))
                .elapsedMs(80L)
                .success(false)
                .error("connection refused with internal stack trace")
                .build();

        service.record(record);

        ArgumentCaptor<McpToolCallAuditPo> captor = ArgumentCaptor.forClass(McpToolCallAuditPo.class);
        verify(mapper).insert(captor.capture());
        McpToolCallAuditPo inserted = captor.getValue();
        assertThat(inserted.getSourceType()).isEqualTo("WORKFLOW");
        assertThat(inserted.getWorkflowRunId()).isEqualTo(100L);
        assertThat(inserted.getWorkflowNodeKey()).isEqualTo("code_task");
        assertThat(inserted.getArgumentKeys()).containsExactly("task");
        assertThat(inserted.getSuccess()).isZero();
        assertThat(inserted.getErrorSummary()).contains("connection refused");
    }

    @Test
    void listByTraceIdReturnsGovernanceSummariesAndErrorCategory() {
        McpToolCallAuditServiceImpl service = new McpToolCallAuditServiceImpl(mapper);
        McpToolCallAuditPo po = new McpToolCallAuditPo();
        po.setTraceId("trace-1");
        po.setToolName("search");
        po.setStatus("FAILED");
        po.setArgumentKeys(List.of("query"));
        po.setArgumentSummary("argumentKeys=[query]");
        po.setResultSummary("partial result");
        po.setErrorSummary("request timeout after 3000ms");
        po.setSuccess(0);
        when(mapper.selectList(any())).thenReturn(List.of(po));

        var records = service.listByTraceId("trace-1");

        assertThat(records).singleElement()
                .satisfies(resp -> {
                    assertThat(resp.getArgumentSummary()).isEqualTo("argumentKeys=[query]");
                    assertThat(resp.getResultSummary()).isEqualTo("partial result");
                    assertThat(resp.getErrorCategory()).isEqualTo("TIMEOUT");
                });
    }
}
