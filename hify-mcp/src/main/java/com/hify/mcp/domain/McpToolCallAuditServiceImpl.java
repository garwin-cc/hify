package com.hify.mcp.domain;

import com.hify.mcp.api.McpToolCallAuditRecord;
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
        po.setConversationSessionId(record.getConversationSessionId());
        po.setConversationMessageId(record.getConversationMessageId());
        po.setWorkflowRunId(record.getWorkflowRunId());
        po.setWorkflowNodeKey(record.getWorkflowNodeKey());
        po.setMcpServerId(record.getMcpServerId());
        po.setToolName(record.getToolName());
        po.setArgumentKeys(argumentKeys(record));
        po.setArgumentSummary("argumentKeys=" + po.getArgumentKeys());
        po.setSuccess(record.isSuccess() ? 1 : 0);
        po.setElapsedMs(record.getElapsedMs());
        po.setResultSummary(abbreviate(record.getResult()));
        po.setErrorSummary(abbreviate(record.getError()));
        mapper.insert(po);
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
}
