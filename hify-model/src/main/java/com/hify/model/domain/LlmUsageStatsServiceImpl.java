package com.hify.model.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.model.api.LlmCallContext;
import com.hify.model.api.LlmCallStatRecord;
import com.hify.model.api.LlmUsageQuery;
import com.hify.model.api.LlmUsageStatsResp;
import com.hify.model.api.LlmUsageStatsService;
import com.hify.model.infra.LlmCallStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlmUsageStatsServiceImpl implements LlmUsageStatsService {

    private final LlmCallStatMapper llmCallStatMapper;

    @Override
    public void record(LlmCallStatRecord record) {
        if (record == null || record.getProviderId() == null || record.getModelConfigId() == null) {
            return;
        }
        try {
            LlmCallContext context = record.getContext();
            LlmCallStatPo po = new LlmCallStatPo();
            po.setTraceId(valueOrEmpty(record.getTraceId()));
            po.setUserId(context == null ? null : context.getUserId());
            po.setProjectId(context == null ? null : context.getProjectId());
            po.setAppId(context == null ? null : context.getAppId());
            po.setAgentId(context == null ? null : context.getAgentId());
            po.setProviderId(record.getProviderId());
            po.setProviderType(valueOrEmpty(record.getProviderType()));
            po.setModelConfigId(record.getModelConfigId());
            po.setModelId(valueOrEmpty(record.getModelId()));
            po.setCallType(StringUtils.hasText(record.getCallType()) ? record.getCallType() : "CHAT");
            po.setSuccess(record.isSuccess() ? 1 : 0);
            po.setFallbackUsed(record.isFallbackUsed() ? 1 : 0);
            po.setInputTokens(Math.max(0, record.getInputTokens()));
            po.setOutputTokens(Math.max(0, record.getOutputTokens()));
            po.setLatencyMs(Math.max(0, record.getLatencyMs()));
            po.setErrorCode(valueOrEmpty(record.getErrorCode()));
            po.setErrorMessage(truncate(valueOrEmpty(record.getErrorMessage()), 500));
            llmCallStatMapper.insert(po);
        } catch (Exception e) {
            log.warn("llm usage stat record failed providerId={} modelConfigId={} message={}",
                    record.getProviderId(), record.getModelConfigId(), e.getMessage());
        }
    }

    @Override
    public List<LlmUsageStatsResp> query(LlmUsageQuery query) {
        LlmUsageQuery q = query == null ? new LlmUsageQuery() : query;
        List<LlmCallStatPo> rows = llmCallStatMapper.selectList(buildQuery(q));
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        for (LlmCallStatPo row : rows) {
            String key = groupKey(q.getGroupBy(), row);
            groups.computeIfAbsent(key, ignored -> new Accumulator(key, key)).add(row);
        }
        return groups.values().stream().map(Accumulator::toResp).toList();
    }

    private LambdaQueryWrapper<LlmCallStatPo> buildQuery(LlmUsageQuery q) {
        return new LambdaQueryWrapper<LlmCallStatPo>()
                .eq(LlmCallStatPo::getDeleted, 0)
                .ge(q.getStartAt() != null, LlmCallStatPo::getCreatedAt, q.getStartAt())
                .lt(q.getEndAt() != null, LlmCallStatPo::getCreatedAt, q.getEndAt())
                .eq(q.getProviderId() != null, LlmCallStatPo::getProviderId, q.getProviderId())
                .eq(q.getModelConfigId() != null, LlmCallStatPo::getModelConfigId, q.getModelConfigId())
                .eq(q.getUserId() != null, LlmCallStatPo::getUserId, q.getUserId())
                .eq(q.getProjectId() != null, LlmCallStatPo::getProjectId, q.getProjectId())
                .eq(q.getAppId() != null, LlmCallStatPo::getAppId, q.getAppId())
                .orderByDesc(LlmCallStatPo::getCreatedAt)
                .last("LIMIT 10000");
    }

    private static String groupKey(String groupBy, LlmCallStatPo row) {
        String normalized = groupBy == null ? "" : groupBy.trim().toUpperCase();
        return switch (normalized) {
            case "USER" -> "USER:" + row.getUserId();
            case "APP" -> "APP:" + row.getAppId();
            case "PROJECT" -> "PROJECT:" + row.getProjectId();
            case "MODEL" -> "MODEL:" + row.getModelConfigId() + ":" + row.getModelId();
            case "PROVIDER" -> "PROVIDER:" + row.getProviderId() + ":" + row.getProviderType();
            default -> "ALL";
        };
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static class Accumulator {
        private final String key;
        private final String name;
        private long callCount;
        private long successCount;
        private long inputTokens;
        private long outputTokens;
        private long latencyMs;

        private Accumulator(String key, String name) {
            this.key = key;
            this.name = name;
        }

        private void add(LlmCallStatPo row) {
            callCount++;
            if (Integer.valueOf(1).equals(row.getSuccess())) {
                successCount++;
            }
            inputTokens += row.getInputTokens() == null ? 0 : row.getInputTokens();
            outputTokens += row.getOutputTokens() == null ? 0 : row.getOutputTokens();
            latencyMs += row.getLatencyMs() == null ? 0 : row.getLatencyMs();
        }

        private LlmUsageStatsResp toResp() {
            LlmUsageStatsResp resp = new LlmUsageStatsResp();
            resp.setGroupKey(key);
            resp.setGroupName(name);
            resp.setCallCount(callCount);
            resp.setSuccessCount(successCount);
            resp.setFailureCount(callCount - successCount);
            resp.setInputTokens(inputTokens);
            resp.setOutputTokens(outputTokens);
            resp.setTotalTokens(inputTokens + outputTokens);
            resp.setFailureRate(callCount == 0 ? 0 : (double) (callCount - successCount) / callCount);
            resp.setAvgLatencyMs(callCount == 0 ? 0 : (double) latencyMs / callCount);
            return resp;
        }
    }
}
