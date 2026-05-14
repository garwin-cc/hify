package com.hify.model.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.auth.api.AuditLogRecord;
import com.hify.auth.api.AuditLogService;
import com.hify.auth.api.AuthService;
import com.hify.auth.api.CurrentUser;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.model.api.*;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.domain.adapter.ProviderAdapter;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderHealthMapper;
import com.hify.model.infra.ProviderHealthPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderServiceImpl implements ProviderService {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
    private static final String API_KEY_PATTERN = "^(sk|sk-ant|sk-or|AIza)[A-Za-z0-9_\\-.]+$";

    private final ProviderMapper         providerMapper;
    private final ModelConfigMapper      modelConfigMapper;
    private final ProviderHealthMapper   providerHealthMapper;
    private final ProviderAdapterFactory providerAdapterFactory;
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

    // ── 列表 ──────────────────────────────────────────────────────────

    @Override
    public PageResult<ProviderListItemResp> list(ProviderQuery query) {
        Page<ProviderPo> pageParam = PageHelper.toPage(query.getPage(), query.getPageSize());
        LambdaQueryWrapper<ProviderPo> wrapper = new LambdaQueryWrapper<ProviderPo>()
                .eq(query.getType()    != null, ProviderPo::getType,    query.getType())
                .eq(query.getEnabled() != null, ProviderPo::getEnabled, query.getEnabled())
                .orderByAsc(ProviderPo::getSortOrder)
                .orderByDesc(ProviderPo::getCreatedAt);
        IPage<ProviderPo> page = providerMapper.selectPage(pageParam, wrapper);

        if (page.getRecords().isEmpty()) {
            return PageResult.ok(List.of(), 0, query.getPage(), query.getPageSize());
        }

        List<Long> ids = page.getRecords().stream().map(ProviderPo::getId).toList();

        Map<Long, ProviderHealthPo> healthMap = providerHealthMapper
                .selectList(new LambdaQueryWrapper<ProviderHealthPo>()
                        .in(ProviderHealthPo::getProviderId, ids))
                .stream()
                .collect(Collectors.toMap(ProviderHealthPo::getProviderId, h -> h));

        Map<Long, List<ModelConfigPo>> modelMap = modelConfigMapper
                .selectList(new LambdaQueryWrapper<ModelConfigPo>()
                        .in(ModelConfigPo::getProviderId, ids)
                        .orderByAsc(ModelConfigPo::getSortOrder)
                        .orderByAsc(ModelConfigPo::getId))
                .stream()
                .collect(Collectors.groupingBy(ModelConfigPo::getProviderId));

        return PageHelper.toPageResult(page, po -> toListItemResp(po,
                healthMap.get(po.getId()),
                modelMap.getOrDefault(po.getId(), List.of())));
    }

    // ── 详情 ──────────────────────────────────────────────────────────

    @Override
    @Cacheable(cacheNames = "provider-cache", key = "'detail:' + #id")
    public ProviderDetailResp getById(Long id) {
        ProviderPo po = findOrThrow(id);

        List<ModelConfigResp> modelConfigs = modelConfigMapper
                .selectList(new LambdaQueryWrapper<ModelConfigPo>()
                        .eq(ModelConfigPo::getProviderId, id)
                        .orderByAsc(ModelConfigPo::getSortOrder)
                        .orderByAsc(ModelConfigPo::getId))
                .stream()
                .map(ProviderServiceImpl::toModelConfigResp)
                .toList();

        ProviderHealthPo healthPo = providerHealthMapper.selectByProviderId(id);

        ProviderDetailResp detail = new ProviderDetailResp();
        detail.setProvider(toResp(po));
        detail.setModelConfigs(modelConfigs);
        detail.setHealth(healthPo != null ? toHealthResp(healthPo) : null);
        return detail;
    }

    // ── 创建 ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ProviderResp create(CreateProviderReq req) {
        validateCreateRequest(req);
        checkNameUnique(req.getName(), null);
        validateApiKeyFormat(req.getApiKey());

        ProviderPo po = new ProviderPo();
        po.setName(req.getName());
        po.setType(req.getType().toUpperCase());
        po.setBaseUrl(req.getBaseUrl() != null ? req.getBaseUrl() : "");
        po.setAuthConfig(buildAuthConfig(req.getApiKey()));
        po.setEnabled(1);
        po.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        providerMapper.insert(po);
        log.info("created provider id={} name={} type={}", po.getId(), po.getName(), po.getType());
        recordAudit("PROVIDER_CREATE", po, null, providerAudit(po), true, null);
        return toResp(po);
    }

    // ── 更新 ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ProviderResp update(Long id, UpdateProviderReq req) {
        ProviderPo po = findOrThrow(id);
        Map<String, Object> before = providerAudit(po);

        if (req.getName() != null && !req.getName().equals(po.getName())) {
            checkNameUnique(req.getName(), id);
            po.setName(req.getName());
        }
        if (req.getType()      != null) po.setType(req.getType().toUpperCase());
        if (req.getBaseUrl()   != null) po.setBaseUrl(req.getBaseUrl());
        if (req.getApiKey()    != null && !req.getApiKey().isBlank()) {
            po.setAuthConfig(buildAuthConfig(req.getApiKey()));
        }
        if (req.getEnabled()   != null) po.setEnabled(req.getEnabled());
        if (req.getSortOrder() != null) po.setSortOrder(req.getSortOrder());

        providerMapper.updateById(po);
        log.info("updated provider id={}", id);
        recordAudit("PROVIDER_UPDATE", po, before, providerAudit(po), true, null);
        return toResp(po);
    }

    // ── 删除 ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public void delete(Long id) {
        ProviderPo po = findOrThrow(id);
        Map<String, Object> before = providerAudit(po);
        providerMapper.deleteById(id);
        log.info("deleted provider id={}", id);
        recordAudit("PROVIDER_DELETE", po, before, Map.of(), true, null);
    }

    // ── 启用 / 禁用切换 ───────────────────────────────────────────────

    @Override
    @Transactional
    @CacheEvict(cacheNames = "provider-cache", allEntries = true)
    public ProviderResp toggle(Long id) {
        ProviderPo po = findOrThrow(id);
        po.setEnabled(po.getEnabled() == 1 ? 0 : 1);
        providerMapper.updateById(po);
        log.info("toggled provider id={} enabled={}", id, po.getEnabled());
        recordAudit("PROVIDER_TOGGLE", po, null, providerAudit(po), true, null);
        return toResp(po);
    }

    // ── 连通性测试 ────────────────────────────────────────────────────

    @Override
    @CacheEvict(cacheNames = "provider-cache", key = "'detail:' + #providerId")
    public ConnectivityTestResult test(Long providerId) {
        ProviderPo po = findOrThrow(providerId);
        ProviderAdapter adapter = providerAdapterFactory.getAdapter(po.getType());
        ConnectivityTestResult result = adapter.testConnection(po);
        persistHealth(providerId, result);
        if (result.isSuccess()) {
            syncDiscoveredModels(po, result.getModelIds());
        }
        log.info("connectivity test provider id={} type={} success={} latency={}ms",
                providerId, po.getType(), result.isSuccess(), result.getLatencyMs());
        recordAudit("PROVIDER_TEST", po, null,
                Map.of("success", result.isSuccess(), "latencyMs", result.getLatencyMs(),
                        "modelCount", result.getModelCount() == null ? 0 : result.getModelCount()),
                result.isSuccess(), result.getErrorMessage());
        return result;
    }

    // ── 内部工具方法 ──────────────────────────────────────────────────

    private ProviderPo findOrThrow(Long id) {
        ProviderPo po = providerMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.PROVIDER_NOT_FOUND, "提供商 ID=" + id + " 不存在");
        }
        return po;
    }

    private void checkNameUnique(String name, Long excludeId) {
        long count = providerMapper.selectCount(new LambdaQueryWrapper<ProviderPo>()
                .eq(ProviderPo::getName, name)
                .ne(excludeId != null, ProviderPo::getId, excludeId));
        if (count > 0) {
            throw new BizException(ErrorCode.PROVIDER_NAME_DUPLICATE, "提供商名称「" + name + "」已存在");
        }
    }

    private static void validateCreateRequest(CreateProviderReq req) {
        Set<ConstraintViolation<CreateProviderReq>> violations = VALIDATOR.validate(req);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    private static void validateApiKeyFormat(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        if (!apiKey.matches(API_KEY_PATTERN)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "API Key 格式不合法");
        }
    }

    private static Map<String, Object> buildAuthConfig(String apiKey) {
        Map<String, Object> config = new HashMap<>();
        if (apiKey != null && !apiKey.isBlank()) {
            config.put("apiKey", apiKey);
        }
        return config;
    }

    private Map<String, Object> providerAudit(ProviderPo po) {
        Map<String, Object> value = new HashMap<>();
        value.put("id", po.getId());
        value.put("name", po.getName());
        value.put("type", po.getType());
        value.put("baseUrl", po.getBaseUrl());
        value.put("enabled", po.getEnabled());
        value.put("sortOrder", po.getSortOrder());
        return value;
    }

    private void recordAudit(String action, ProviderPo provider, Map<String, Object> before,
                             Map<String, Object> after, boolean success, String errorMessage) {
        if (auditLogService == null) {
            return;
        }
        CurrentUser user = currentUser();
        auditLogService.record(AuditLogRecord.builder()
                .traceId(TraceContext.ensureTraceId())
                .actorUserId(user == null ? null : user.getId())
                .actorUsername(user == null ? "" : user.getUsername())
                .action(action)
                .resourceType("PROVIDER")
                .resourceId(provider == null ? null : provider.getId())
                .resourceName(provider == null ? "" : provider.getName())
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

    private static ProviderResp toResp(ProviderPo po) {
        ProviderResp resp = new ProviderResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setType(po.getType());
        resp.setBaseUrl(po.getBaseUrl());
        resp.setEnabled(po.getEnabled());
        resp.setSortOrder(po.getSortOrder());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private static ProviderListItemResp toListItemResp(ProviderPo po,
                                                        ProviderHealthPo health,
                                                        List<ModelConfigPo> models) {
        ProviderListItemResp item = new ProviderListItemResp();
        item.setId(po.getId());
        item.setName(po.getName());
        item.setType(po.getType());
        item.setBaseUrl(po.getBaseUrl());
        item.setEnabled(po.getEnabled());
        item.setSortOrder(po.getSortOrder());
        item.setCreatedAt(po.getCreatedAt());
        if (health != null) {
            item.setHealthStatus(health.getStatus());
            item.setLatencyMs(health.getLatencyMs());
        }
        List<ModelConfigResp> modelResps = models.stream()
                .map(ProviderServiceImpl::toModelConfigResp).toList();
        long enabledCount = models.stream()
                .filter(m -> Integer.valueOf(1).equals(m.getEnabled())).count();
        item.setModelCount((int) enabledCount);
        item.setModels(modelResps);
        return item;
    }

    private static ModelConfigResp toModelConfigResp(ModelConfigPo po) {
        ModelConfigResp resp = new ModelConfigResp();
        resp.setId(po.getId());
        resp.setProviderId(po.getProviderId());
        resp.setName(po.getName());
        resp.setModelId(po.getModelId());
        resp.setModelType(po.getModelType() == null ? "CHAT" : po.getModelType());
        resp.setContextSize(po.getContextSize());
        resp.setExtraParams(po.getExtraParams());
        resp.setEnabled(po.getEnabled());
        resp.setSortOrder(po.getSortOrder());
        return resp;
    }

    private static ProviderHealthResp toHealthResp(ProviderHealthPo po) {
        ProviderHealthResp resp = new ProviderHealthResp();
        resp.setStatus(po.getStatus());
        resp.setLastCheckAt(po.getLastCheckAt());
        resp.setLastSuccessAt(po.getLastSuccessAt());
        resp.setFailCount(po.getFailCount());
        resp.setLatencyMs(po.getLatencyMs());
        resp.setErrorMessage(po.getErrorMessage());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private void persistHealth(Long providerId, ConnectivityTestResult result) {
        LocalDateTime now = LocalDateTime.now();

        ProviderHealthPo health = providerHealthMapper.selectByProviderId(providerId);
        if (health == null) {
            health = new ProviderHealthPo();
            health.setProviderId(providerId);
            health.setFailCount(0);
        }

        health.setLastCheckAt(now);
        health.setLatencyMs(result.getLatencyMs());
        health.setUpdatedAt(now);

        if (result.isSuccess()) {
            health.setStatus("UP");
            health.setLastSuccessAt(now);
            health.setFailCount(0);
            health.setErrorMessage("");
        } else {
            health.setStatus("DOWN");
            health.setFailCount(health.getFailCount() == null ? 1 : health.getFailCount() + 1);
            health.setErrorMessage(result.getErrorMessage());
        }

        providerHealthMapper.upsert(health);
    }

    private void syncDiscoveredModels(ProviderPo provider, List<String> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            return;
        }

        List<ModelConfigPo> existingModels = modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>()
                        .eq(ModelConfigPo::getProviderId, provider.getId()));
        Map<String, ModelConfigPo> existingByModelId = existingModels.stream()
                .collect(Collectors.toMap(ModelConfigPo::getModelId, model -> model, (left, right) -> left));

        int sortOrder = existingModels.stream()
                .map(ModelConfigPo::getSortOrder)
                .filter(order -> order != null)
                .max(Integer::compareTo)
                .orElse(0);

        for (String modelId : modelIds) {
            if (modelId == null || modelId.isBlank() || existingByModelId.containsKey(modelId)) {
                continue;
            }
            ModelConfigPo model = new ModelConfigPo();
            model.setProviderId(provider.getId());
            model.setName(modelId);
            model.setModelId(modelId);
            model.setModelType(isEmbeddingModel(modelId) ? "EMBEDDING" : "CHAT");
            model.setContextSize(null);
            model.setExtraParams(null);
            model.setEnabled(1);
            model.setSortOrder(++sortOrder);
            modelConfigMapper.insert(model);
        }
    }

    private static boolean isEmbeddingModel(String modelId) {
        String lower = modelId.toLowerCase();
        return lower.contains("embed")
                || lower.contains("embedding")
                || lower.contains("bge")
                || lower.contains("nomic");
    }
}
