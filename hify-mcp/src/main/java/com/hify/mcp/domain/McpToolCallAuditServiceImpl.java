package com.hify.mcp.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.mcp.api.McpToolCallAuditRecord;
import com.hify.mcp.api.McpToolCallAuditQuery;
import com.hify.mcp.api.McpToolCallAuditResp;
import com.hify.mcp.api.McpToolCallAuditService;
import com.hify.mcp.infra.McpToolCallAuditMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class McpToolCallAuditServiceImpl implements McpToolCallAuditService {

    private static final int SUMMARY_LIMIT = 500;

    private final McpToolCallAuditMapper mapper;

    @Override
    public void record(McpToolCallAuditRecord record) {
        if (record == null) {
            return;
        }
        McpToolCallAuditPo po = new McpToolCallAuditPo();
        po.setSourceType(blankToDefault(record.getSourceType(), "UNKNOWN"));
        po.setTraceId(record.getTraceId());
        po.setWorkspaceId(record.getWorkspaceId());
        po.setProjectId(record.getProjectId());
        po.setAgentId(record.getAgentId());
        po.setAppId(record.getAppId());
        po.setApiKeyId(record.getApiKeyId());
        po.setUserId(record.getUserId());
        po.setConversationSessionId(record.getConversationSessionId());
        po.setConversationMessageId(record.getConversationMessageId());
        po.setWorkflowId(record.getWorkflowId());
        po.setWorkflowRunId(record.getWorkflowRunId());
        po.setWorkflowNodeKey(record.getWorkflowNodeKey());
        po.setMcpServerId(record.getMcpServerId());
        po.setToolName(record.getToolName());
        po.setStatus(blankToDefault(record.getStatus(), record.isSuccess() ? "SUCCESS" : "FAILED"));
        po.setRetryCount(record.getRetryCount() == null ? 0 : record.getRetryCount());
        po.setTimeoutMs(record.getTimeoutMs());
        po.setSourceId(record.getSourceId());
        po.setArgumentKeys(argumentKeys(record));
        po.setArgumentSummary("argumentKeys=" + po.getArgumentKeys());
        po.setSuccess(record.isSuccess() ? 1 : 0);
        po.setElapsedMs(record.getElapsedMs());
        po.setResultSummary(abbreviate(record.getResult()));
        po.setErrorSummary(abbreviate(record.getError()));
        mapper.insert(po);
    }

    @Override
    public List<McpToolCallAuditResp> listByTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return List.of();
        }
        return mapper.selectList(Wrappers.lambdaQuery(McpToolCallAuditPo.class)
                        .eq(McpToolCallAuditPo::getTraceId, traceId)
                        .orderByAsc(McpToolCallAuditPo::getId))
                .stream()
                .map(McpToolCallAuditServiceImpl::toResp)
                .toList();
    }

    @Override
    public PageResult<McpToolCallAuditResp> list(McpToolCallAuditQuery query) {
        Page<McpToolCallAuditPo> page = PageHelper.toPage(query.getPage(), query.getSize());
        return PageHelper.toPageResult(mapper.selectPage(page,
                Wrappers.lambdaQuery(McpToolCallAuditPo.class)
                        .eq(query.getProjectId() != null, McpToolCallAuditPo::getProjectId, query.getProjectId())
                        .eq(query.getAgentId() != null, McpToolCallAuditPo::getAgentId, query.getAgentId())
                        .eq(query.getAppId() != null, McpToolCallAuditPo::getAppId, query.getAppId())
                        .eq(query.getUserId() != null, McpToolCallAuditPo::getUserId, query.getUserId())
                        .eq(query.getWorkflowId() != null, McpToolCallAuditPo::getWorkflowId, query.getWorkflowId())
                        .eq(query.getWorkflowRunId() != null, McpToolCallAuditPo::getWorkflowRunId, query.getWorkflowRunId())
                        .eq(query.getMcpServerId() != null, McpToolCallAuditPo::getMcpServerId, query.getMcpServerId())
                        .eq(query.getToolName() != null && !query.getToolName().isBlank(), McpToolCallAuditPo::getToolName, query.getToolName())
                        .eq(query.getStatus() != null && !query.getStatus().isBlank(), McpToolCallAuditPo::getStatus, query.getStatus())
                        .eq(query.getTraceId() != null && !query.getTraceId().isBlank(), McpToolCallAuditPo::getTraceId, query.getTraceId())
                        .ge(query.getMinElapsedMs() != null, McpToolCallAuditPo::getElapsedMs, query.getMinElapsedMs())
                        .le(query.getMaxElapsedMs() != null, McpToolCallAuditPo::getElapsedMs, query.getMaxElapsedMs())
                        .ge(query.getCreatedAtStart() != null, McpToolCallAuditPo::getCreatedAt, query.getCreatedAtStart())
                        .le(query.getCreatedAtEnd() != null, McpToolCallAuditPo::getCreatedAt, query.getCreatedAtEnd())
                        .orderByDesc(McpToolCallAuditPo::getCreatedAt)
                        .orderByDesc(McpToolCallAuditPo::getId)), McpToolCallAuditServiceImpl::toResp);
    }

    private static McpToolCallAuditResp toResp(McpToolCallAuditPo po) {
        McpToolCallAuditResp resp = new McpToolCallAuditResp();
        resp.setId(po.getId());
        resp.setTraceId(po.getTraceId());
        resp.setProjectId(po.getProjectId());
        resp.setAgentId(po.getAgentId());
        resp.setUserId(po.getUserId());
        resp.setWorkflowId(po.getWorkflowId());
        resp.setWorkflowRunId(po.getWorkflowRunId());
        resp.setMcpServerId(po.getMcpServerId());
        resp.setToolName(po.getToolName());
        resp.setStatus(po.getStatus());
        resp.setArgumentKeys(po.getArgumentKeys());
        resp.setArgumentSummary(po.getArgumentSummary());
        resp.setResultSummary(po.getResultSummary());
        resp.setErrorCategory(errorCategory(po.getErrorSummary()));
        resp.setElapsedMs(po.getElapsedMs());
        resp.setSuccess(po.getSuccess() != null && po.getSuccess() == 1);
        resp.setErrorSummary(po.getErrorSummary());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private static List<String> argumentKeys(McpToolCallAuditRecord record) {
        if (record.getArguments() == null || record.getArguments().isEmpty()) {
            return List.of();
        }
        return record.getArguments().keySet().stream().sorted().toList();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String abbreviate(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= SUMMARY_LIMIT ? compact : compact.substring(0, SUMMARY_LIMIT);
    }

    private static String errorCategory(String errorSummary) {
        if (errorSummary == null || errorSummary.isBlank()) {
            return null;
        }
        String lower = errorSummary.toLowerCase();
        if (lower.contains("timeout") || lower.contains("timed out") || errorSummary.contains("超时")) {
            return "TIMEOUT";
        }
        if (lower.contains("param") || lower.contains("argument")
                || lower.contains("validation") || errorSummary.contains("参数")) {
            return "PARAM_ERROR";
        }
        if (lower.contains("connection") || lower.contains("connect")
                || lower.contains("refused") || errorSummary.contains("连接")) {
            return "CONNECTION_ERROR";
        }
        return "MCP_ERROR";
    }
}
