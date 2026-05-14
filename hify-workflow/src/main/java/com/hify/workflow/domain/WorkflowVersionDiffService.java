package com.hify.workflow.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.workflow.api.WorkflowDetailResp;
import com.hify.workflow.api.WorkflowEdgeDto;
import com.hify.workflow.api.WorkflowNodeDto;
import com.hify.workflow.api.WorkflowVersionDiffResp;
import com.hify.workflow.infra.WorkflowVersionDiffMapper;
import com.hify.workflow.infra.WorkflowVersionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowVersionDiffService {

    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowVersionDiffMapper workflowVersionDiffMapper;
    private final ObjectMapper objectMapper;

    public WorkflowVersionDiffResp diff(Long workflowId, Integer leftVersionNo, Integer rightVersionNo) {
        WorkflowVersionPo left = findVersion(workflowId, leftVersionNo);
        WorkflowVersionPo right = findVersion(workflowId, rightVersionNo);
        Map<String, Object> summary = diffSnapshots(parse(left), parse(right));

        WorkflowVersionDiffPo po = new WorkflowVersionDiffPo();
        po.setWorkflowId(workflowId);
        po.setLeftVersionId(left.getId());
        po.setRightVersionId(right.getId());
        po.setSummaryJson(toJson(summary));
        workflowVersionDiffMapper.insert(po);

        WorkflowVersionDiffResp resp = new WorkflowVersionDiffResp();
        resp.setWorkflowId(workflowId);
        resp.setLeftVersionNo(left.getVersionNo());
        resp.setRightVersionNo(right.getVersionNo());
        resp.setSummary(toJsonNode(summary));
        return resp;
    }

    public String checksum(String snapshotJson) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((snapshotJson == null ? "" : snapshotJson).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte b : bytes) {
                value.append(String.format("%02x", b));
            }
            return value.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private WorkflowVersionPo findVersion(Long workflowId, Integer versionNo) {
        if (versionNo == null || versionNo <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "版本号不合法");
        }
        WorkflowVersionPo version = workflowVersionMapper.selectOne(Wrappers.lambdaQuery(WorkflowVersionPo.class)
                .eq(WorkflowVersionPo::getWorkflowId, workflowId)
                .eq(WorkflowVersionPo::getVersionNo, versionNo)
                .last("LIMIT 1"));
        if (version == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "工作流版本不存在: v" + versionNo);
        }
        return version;
    }

    private WorkflowDetailResp parse(WorkflowVersionPo version) {
        try {
            return objectMapper.readValue(version.getSnapshotJson(), WorkflowDetailResp.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.WORKFLOW_CONFIG_INVALID, "工作流版本快照解析失败", e);
        }
    }

    private Map<String, Object> diffSnapshots(WorkflowDetailResp left, WorkflowDetailResp right) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("workflowChanged", !safe(left.getName()).equals(safe(right.getName()))
                || !safe(left.getDescription()).equals(safe(right.getDescription()))
                || !safe(left.getStartNodeKey()).equals(safe(right.getStartNodeKey())));
        summary.put("nodes", diffNodes(left.getNodes(), right.getNodes()));
        summary.put("edges", diffEdges(left.getEdges(), right.getEdges()));
        return summary;
    }

    private Map<String, Object> diffNodes(List<WorkflowNodeDto> left, List<WorkflowNodeDto> right) {
        Map<String, WorkflowNodeDto> leftMap = left.stream().collect(Collectors.toMap(WorkflowNodeDto::getNodeKey, Function.identity()));
        Map<String, WorkflowNodeDto> rightMap = right.stream().collect(Collectors.toMap(WorkflowNodeDto::getNodeKey, Function.identity()));
        return diffMaps(leftMap, rightMap);
    }

    private Map<String, Object> diffEdges(List<WorkflowEdgeDto> left, List<WorkflowEdgeDto> right) {
        Map<String, WorkflowEdgeDto> leftMap = left.stream().collect(Collectors.toMap(this::edgeKey, Function.identity(), (a, b) -> a));
        Map<String, WorkflowEdgeDto> rightMap = right.stream().collect(Collectors.toMap(this::edgeKey, Function.identity(), (a, b) -> a));
        return diffMaps(leftMap, rightMap);
    }

    private <T> Map<String, Object> diffMaps(Map<String, T> left, Map<String, T> right) {
        List<String> added = right.keySet().stream().filter(key -> !left.containsKey(key)).sorted().toList();
        List<String> removed = left.keySet().stream().filter(key -> !right.containsKey(key)).sorted().toList();
        List<String> changed = right.keySet().stream()
                .filter(left::containsKey)
                .filter(key -> !toJson(left.get(key)).equals(toJson(right.get(key))))
                .sorted()
                .toList();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("added", added);
        value.put("removed", removed);
        value.put("changed", changed);
        return value;
    }

    private String edgeKey(WorkflowEdgeDto edge) {
        return safe(edge.getSourceNodeKey()) + "->" + safe(edge.getTargetNodeKey()) + ":" + safe(edge.getConditionExpression());
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private JsonNode toJsonNode(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
