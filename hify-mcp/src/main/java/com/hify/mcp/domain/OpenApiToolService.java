package com.hify.mcp.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.mcp.api.CreateOpenApiSourceReq;
import com.hify.mcp.api.OpenApiSourceResp;
import com.hify.mcp.api.OpenApiToolResp;
import com.hify.mcp.infra.OpenApiToolMapper;
import com.hify.mcp.infra.OpenApiToolSourceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class OpenApiToolService {

    private final OpenApiToolSourceMapper sourceMapper;
    private final OpenApiToolMapper toolMapper;
    private final ObjectMapper objectMapper;
    private final McpEndpointGuard endpointGuard;

    @Transactional
    public OpenApiSourceResp create(CreateOpenApiSourceReq req) {
        endpointGuard.validate(req.getBaseUrl());
        if (req.getSpecJson() == null || req.getSpecJson().isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "OpenAPI specJson 不能为空");
        }
        OpenApiToolSourcePo po = new OpenApiToolSourcePo();
        po.setWorkspaceId(req.getWorkspaceId() == null ? 1L : req.getWorkspaceId());
        po.setProjectId(req.getProjectId() == null ? 1L : req.getProjectId());
        po.setName(req.getName());
        po.setDescription(req.getDescription() == null ? "" : req.getDescription());
        po.setBaseUrl(req.getBaseUrl());
        po.setSpecJson(req.getSpecJson());
        po.setSecretId(req.getSecretId());
        po.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        po.setStatus("ACTIVE");
        sourceMapper.insert(po);
        sync(po.getId());
        return toResp(po);
    }

    @Transactional
    public List<OpenApiToolResp> sync(Long sourceId) {
        OpenApiToolSourcePo source = sourceMapper.selectById(sourceId);
        if (source == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "OpenAPI 工具源不存在");
        }
        endpointGuard.validate(source.getBaseUrl());
        toolMapper.delete(Wrappers.lambdaQuery(OpenApiToolPo.class)
                .eq(OpenApiToolPo::getSourceId, sourceId));
        List<OpenApiToolPo> tools = parseTools(source);
        for (OpenApiToolPo tool : tools) {
            toolMapper.insert(tool);
        }
        return tools.stream().map(this::toToolResp).toList();
    }

    public List<OpenApiToolResp> listTools(Long sourceId) {
        return toolMapper.selectList(Wrappers.lambdaQuery(OpenApiToolPo.class)
                        .eq(OpenApiToolPo::getSourceId, sourceId)
                        .orderByAsc(OpenApiToolPo::getId))
                .stream()
                .map(this::toToolResp)
                .toList();
    }

    private List<OpenApiToolPo> parseTools(OpenApiToolSourcePo source) {
        JsonNode root = objectMapper.valueToTree(source.getSpecJson());
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            return List.of();
        }
        List<OpenApiToolPo> tools = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> pathFields = paths.fields();
        while (pathFields.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathFields.next();
            Iterator<Map.Entry<String, JsonNode>> methodFields = pathEntry.getValue().fields();
            while (methodFields.hasNext()) {
                Map.Entry<String, JsonNode> methodEntry = methodFields.next();
                String method = methodEntry.getKey().toUpperCase();
                if (!List.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(method)) {
                    continue;
                }
                JsonNode operation = methodEntry.getValue();
                OpenApiToolPo tool = new OpenApiToolPo();
                tool.setSourceId(source.getId());
                tool.setName(toolName(method, pathEntry.getKey(), operation));
                tool.setDescription(operation.path("summary").asText(operation.path("description").asText("")));
                tool.setHttpMethod(method);
                tool.setPath(pathEntry.getKey());
                tool.setInputSchema(inputSchema(operation));
                tool.setResponseSchema(Map.of());
                tool.setEnabled(1);
                tools.add(tool);
            }
        }
        return tools;
    }

    private String toolName(String method, String path, JsonNode operation) {
        String operationId = operation.path("operationId").asText("");
        if (StringUtils.hasText(operationId)) {
            return operationId;
        }
        return (method + "_" + path).replaceAll("[^A-Za-z0-9_]+", "_").replaceAll("_+", "_");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> inputSchema(JsonNode operation) {
        JsonNode schema = operation.path("requestBody").path("content").path("application/json").path("schema");
        if (schema.isMissingNode() || schema.isNull()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        return objectMapper.convertValue(schema, Map.class);
    }

    private OpenApiSourceResp toResp(OpenApiToolSourcePo po) {
        OpenApiSourceResp resp = new OpenApiSourceResp();
        resp.setId(po.getId());
        resp.setWorkspaceId(po.getWorkspaceId());
        resp.setProjectId(po.getProjectId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setBaseUrl(po.getBaseUrl());
        resp.setSecretId(po.getSecretId());
        resp.setEnabled(po.getEnabled());
        resp.setStatus(po.getStatus());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private OpenApiToolResp toToolResp(OpenApiToolPo po) {
        OpenApiToolResp resp = new OpenApiToolResp();
        resp.setId(po.getId());
        resp.setSourceId(po.getSourceId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setHttpMethod(po.getHttpMethod());
        resp.setPath(po.getPath());
        resp.setInputSchema(po.getInputSchema());
        resp.setEnabled(po.getEnabled());
        return resp;
    }
}
