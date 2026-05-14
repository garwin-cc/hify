package com.hify.mcp.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.audit.AuditLogRecord;
import com.hify.common.audit.AuditLogService;
import com.hify.auth.api.AuthService;
import com.hify.auth.api.CurrentUser;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.mcp.api.*;
import com.hify.mcp.infra.McpServerMapper;
import com.hify.mcp.infra.McpToolMapper;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class McpServiceImpl implements McpService {

    private final McpServerMapper     mcpServerMapper;
    private final McpToolMapper       mcpToolMapper;
    private final McpSdkClientFactory mcpSdkClientFactory;
    private final McpRawHttpClient    mcpRawHttpClient;
    private AuditLogService auditLogService;
    private AuthService authService;

    @Autowired(required = false)
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Autowired(required = false)
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public PageResult<McpServerListItemResp> list(McpServerQuery query) {
        Page<McpServerPo> pageParam = PageHelper.toPage(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<McpServerPo> wrapper = new LambdaQueryWrapper<McpServerPo>()
                .like(query.getName() != null && !query.getName().isBlank(), McpServerPo::getName, query.getName())
                .eq(query.getEnabled() != null, McpServerPo::getEnabled, query.getEnabled())
                .orderByDesc(McpServerPo::getCreatedAt)
                .orderByDesc(McpServerPo::getId);
        IPage<McpServerPo> page = mcpServerMapper.selectPage(pageParam, wrapper);

        if (page.getRecords().isEmpty()) {
            return PageResult.ok(List.of(), 0, query.getPage(), query.getPageSize());
        }

        List<Long> serverIds = page.getRecords().stream().map(McpServerPo::getId).toList();
        Map<Long, Integer> toolCountMap = mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolPo>()
                        .in(McpToolPo::getMcpServerId, serverIds))
                .stream()
                .collect(Collectors.groupingBy(McpToolPo::getMcpServerId,
                        Collectors.collectingAndThen(Collectors.counting(), Long::intValue)));

        return PageHelper.toPageResult(page, po -> toListItemResp(po, toolCountMap.getOrDefault(po.getId(), 0)));
    }

    @Override
    public McpServerDetailResp getById(Long id) {
        McpServerPo po = findOrThrow(id);
        List<McpToolResp> tools = listToolsByServerId(id);

        McpServerDetailResp detail = new McpServerDetailResp();
        detail.setServer(toResp(po));
        detail.setTools(tools);
        return detail;
    }

    @Override
    @Transactional
    public McpServerResp create(CreateMcpServerReq req) {
        checkNameUnique(req.getName(), null);

        McpServerPo po = new McpServerPo();
        po.setName(req.getName());
        po.setDescription(req.getDescription() != null ? req.getDescription() : "");
        po.setEndpoint(normalizeEndpoint(req.getEndpoint()));
        po.setAuthType("NONE");
        po.setAuthConfig("{}");
        po.setEnabled(req.getEnabled() != null ? req.getEnabled() : 1);
        mcpServerMapper.insert(po);
        log.info("created mcp server id={} name={}", po.getId(), po.getName());
        recordAudit("MCP_CREATE", po, null, mcpAudit(po), true, null);
        return toResp(po);
    }

    @Override
    @Transactional
    public McpServerResp update(Long id, UpdateMcpServerReq req) {
        McpServerPo po = findOrThrow(id);
        Map<String, Object> before = mcpAudit(po);

        if (req.getName() != null && !req.getName().equals(po.getName())) {
            checkNameUnique(req.getName(), id);
            po.setName(req.getName());
        }
        if (req.getDescription() != null) po.setDescription(req.getDescription());
        if (req.getEndpoint() != null) po.setEndpoint(normalizeEndpoint(req.getEndpoint()));
        if (req.getEnabled() != null) po.setEnabled(req.getEnabled());

        mcpServerMapper.updateById(po);
        log.info("updated mcp server id={}", id);
        recordAudit("MCP_UPDATE", po, before, mcpAudit(po), true, null);
        return toResp(po);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        McpServerPo po = findOrThrow(id);
        Map<String, Object> before = mcpAudit(po);
        long bindingCount = mcpServerMapper.countAgentBindings(id);
        if (bindingCount > 0) {
            throw new BizException(ErrorCode.CONFLICT, "已有 Agent 绑定该 MCP Server，不能删除");
        }
        mcpToolMapper.delete(new LambdaQueryWrapper<McpToolPo>().eq(McpToolPo::getMcpServerId, id));
        mcpServerMapper.deleteById(id);
        log.info("deleted mcp server id={}", id);
        recordAudit("MCP_DELETE", po, before, Map.of(), true, null);
    }

    @Override
    @Transactional
    public McpConnectivityTestResult test(Long id) {
        McpServerPo po = findOrThrow(id);
        long start = System.currentTimeMillis();
        McpConnectivityTestResult result = new McpConnectivityTestResult();
        try {
            List<McpToolPo> toolPos = listToolsWithFallback(id, po.getEndpoint());
            fillSuccessResult(id, result, toolPos);
        } catch (Throwable e) {
            result.setSuccess(false);
            result.setMessage(e.getMessage());
            result.setTools(List.of());
            log.warn("mcp server test failed id={} endpoint={}", id, po.getEndpoint(), e);
        } finally {
            result.setLatencyMs(System.currentTimeMillis() - start);
        }
        recordAudit("MCP_TEST", po, null,
                Map.of("success", result.isSuccess(), "latencyMs", result.getLatencyMs(),
                        "toolCount", result.getTools() == null ? 0 : result.getTools().size()),
                result.isSuccess(), result.getMessage());
        return result;
    }

    private List<McpToolPo> listToolsWithFallback(Long serverId, String endpoint) {
        try (McpSyncClient client = mcpSdkClientFactory.create(endpoint)) {
            return client.listTools().tools().stream()
                    .map(tool -> toToolPo(serverId, tool))
                    .toList();
        } catch (Throwable e) {
            log.warn("mcp sdk list tools failed, fallback to raw http serverId={}", serverId, e);
            return mcpRawHttpClient.listTools(serverId, endpoint);
        }
    }

    private void fillSuccessResult(Long serverId, McpConnectivityTestResult result, List<McpToolPo> toolPos) {
        replaceTools(serverId, toolPos);
        result.setSuccess(true);
        result.setMessage("ok");
        result.setTools(toolPos.stream().map(McpServiceImpl::toToolResp).toList());
        log.info("mcp server test success id={} tools={}", serverId, toolPos.size());
    }

    @Override
    public List<McpServerResp> listEnabled() {
        return mcpServerMapper.selectList(
                        new LambdaQueryWrapper<McpServerPo>()
                                .eq(McpServerPo::getEnabled, 1)
                                .orderByAsc(McpServerPo::getId))
                .stream()
                .map(McpServiceImpl::toResp)
                .toList();
    }

    @Override
    public void validateEnabledToolIds(List<Long> toolIds) {
        List<McpToolPo> tools = requireExistingTools(toolIds);
        if (tools.isEmpty()) return;

        ensureToolServersEnabled(tools);
    }

    @Override
    public List<McpToolResp> listEnabledToolsByIds(List<Long> toolIds) {
        List<McpToolPo> tools = requireExistingTools(toolIds);
        if (tools.isEmpty()) return List.of();

        ensureToolServersEnabled(tools);
        Map<Long, McpToolPo> toolMap = tools.stream().collect(Collectors.toMap(McpToolPo::getId, tool -> tool));
        return toolIds.stream()
                .map(toolMap::get)
                .filter(tool -> tool != null)
                .map(McpServiceImpl::toToolResp)
                .toList();
    }

    private List<McpToolPo> requireExistingTools(List<Long> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return List.of();
        }

        List<McpToolPo> tools = mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolPo>()
                .in(McpToolPo::getId, toolIds));
        Set<Long> foundToolIds = tools.stream().map(McpToolPo::getId).collect(Collectors.toSet());
        List<Long> missingToolIds = toolIds.stream()
                .filter(id -> !foundToolIds.contains(id))
                .toList();
        if (!missingToolIds.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP 工具不存在: " + missingToolIds);
        }
        return tools;
    }

    private void ensureToolServersEnabled(List<McpToolPo> tools) {
        Set<Long> serverIds = tools.stream().map(McpToolPo::getMcpServerId).collect(Collectors.toCollection(HashSet::new));
        Set<Long> enabledServerIds = mcpServerMapper.selectList(new LambdaQueryWrapper<McpServerPo>()
                        .in(McpServerPo::getId, serverIds)
                        .eq(McpServerPo::getEnabled, 1))
                .stream()
                .map(McpServerPo::getId)
                .collect(Collectors.toSet());

        List<Long> disabledToolIds = tools.stream()
                .filter(tool -> !enabledServerIds.contains(tool.getMcpServerId()))
                .map(McpToolPo::getId)
                .toList();
        if (!disabledToolIds.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "MCP 工具所属 Server 未启用: " + disabledToolIds);
        }
    }

    private McpServerPo findOrThrow(Long id) {
        McpServerPo po = mcpServerMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "MCP Server ID=" + id + " 不存在");
        }
        return po;
    }

    private void checkNameUnique(String name, Long excludeId) {
        long count = mcpServerMapper.selectCount(new LambdaQueryWrapper<McpServerPo>()
                .eq(McpServerPo::getName, name)
                .ne(excludeId != null, McpServerPo::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "MCP Server 名称「" + name + "」已存在");
        }
    }

    private List<McpToolResp> listToolsByServerId(Long serverId) {
        return mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolPo>()
                        .eq(McpToolPo::getMcpServerId, serverId)
                        .orderByAsc(McpToolPo::getId))
                .stream()
                .map(McpServiceImpl::toToolResp)
                .toList();
    }

    private void replaceTools(Long serverId, List<McpToolPo> tools) {
        List<McpToolPo> existingTools = mcpToolMapper.selectList(new LambdaQueryWrapper<McpToolPo>()
                .eq(McpToolPo::getMcpServerId, serverId));
        Map<String, McpToolPo> existingToolMap = existingTools.stream()
                .collect(Collectors.toMap(McpToolPo::getName, tool -> tool, (left, right) -> left));
        Set<String> latestToolNames = tools.stream().map(McpToolPo::getName).collect(Collectors.toSet());

        for (McpToolPo tool : tools) {
            McpToolPo existingTool = existingToolMap.get(tool.getName());
            if (existingTool == null) {
                mcpToolMapper.insert(tool);
                continue;
            }
            existingTool.setDescription(tool.getDescription());
            existingTool.setInputSchema(tool.getInputSchema());
            mcpToolMapper.updateById(existingTool);
            tool.setId(existingTool.getId());
        }

        existingTools.stream()
                .filter(tool -> !latestToolNames.contains(tool.getName()))
                .forEach(tool -> mcpToolMapper.deleteById(tool.getId()));
    }

    private static McpToolPo toToolPo(Long serverId, McpSchema.Tool tool) {
        McpToolPo po = new McpToolPo();
        po.setMcpServerId(serverId);
        po.setName(tool.name());
        po.setDescription(tool.description() != null ? tool.description() : "");
        po.setInputSchema(toInputSchemaMap(tool.inputSchema()));
        return po;
    }

    private static Map<String, Object> toInputSchemaMap(McpSchema.JsonSchema schema) {
        if (schema == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        putIfNotNull(map, "type", schema.type());
        putIfNotNull(map, "properties", schema.properties());
        putIfNotNull(map, "required", schema.required());
        putIfNotNull(map, "additionalProperties", schema.additionalProperties());
        putIfNotNull(map, "$defs", schema.defs());
        putIfNotNull(map, "definitions", schema.definitions());
        return map;
    }

    private static void putIfNotNull(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String normalizeEndpoint(String endpoint) {
        return endpoint == null ? "" : endpoint.replaceAll("\\s+", "");
    }

    private Map<String, Object> mcpAudit(McpServerPo po) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", po.getId());
        value.put("workspaceId", po.getWorkspaceId());
        value.put("projectId", po.getProjectId());
        value.put("name", po.getName());
        value.put("description", po.getDescription());
        value.put("endpoint", po.getEndpoint());
        value.put("authType", po.getAuthType());
        value.put("enabled", po.getEnabled());
        return value;
    }

    private void recordAudit(String action, McpServerPo server, Map<String, Object> before,
                             Map<String, Object> after, boolean success, String errorMessage) {
        if (auditLogService == null) {
            return;
        }
        CurrentUser user = currentUser();
        auditLogService.record(AuditLogRecord.builder()
                .traceId(TraceContext.ensureTraceId())
                .actorUserId(user == null ? null : user.getId())
                .actorUsername(user == null ? "" : user.getUsername())
                .workspaceId(server == null ? null : server.getWorkspaceId())
                .projectId(server == null ? null : server.getProjectId())
                .action(action)
                .resourceType("MCP_SERVER")
                .resourceId(server == null ? null : server.getId())
                .resourceName(server == null ? "" : server.getName())
                .success(success)
                .errorMessage(errorMessage)
                .before(before)
                .after(after)
                .build());
    }

    private CurrentUser currentUser() {
        if (authService == null) {
            return null;
        }
        try {
            return authService.getCurrentUser();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static McpServerResp toResp(McpServerPo po) {
        McpServerResp resp = new McpServerResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setEndpoint(po.getEndpoint());
        resp.setEnabled(po.getEnabled());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private static McpServerListItemResp toListItemResp(McpServerPo po, Integer toolCount) {
        McpServerListItemResp resp = new McpServerListItemResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setEndpoint(po.getEndpoint());
        resp.setEnabled(po.getEnabled());
        resp.setToolCount(toolCount);
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private static McpToolResp toToolResp(McpToolPo po) {
        McpToolResp resp = new McpToolResp();
        resp.setId(po.getId());
        resp.setMcpServerId(po.getMcpServerId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setInputSchema(po.getInputSchema());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }
}
