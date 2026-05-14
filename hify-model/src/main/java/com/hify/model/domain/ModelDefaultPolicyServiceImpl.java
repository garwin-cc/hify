package com.hify.model.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.model.api.LlmCallContext;
import com.hify.model.api.ModelDefaultPolicyReq;
import com.hify.model.api.ModelDefaultPolicyResp;
import com.hify.model.api.ModelDefaultPolicyService;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ModelDefaultPolicyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ModelDefaultPolicyServiceImpl implements ModelDefaultPolicyService {

    public static final String SCOPE_GLOBAL = "GLOBAL";
    public static final String SCOPE_PROJECT = "PROJECT";
    public static final String SCOPE_APP = "APP";
    public static final String SCOPE_PROVIDER_FALLBACK = "PROVIDER_FALLBACK";

    private final ModelDefaultPolicyMapper policyMapper;
    private final ModelConfigMapper modelConfigMapper;

    @Override
    public List<ModelDefaultPolicyResp> list() {
        return policyMapper.selectList(Wrappers.lambdaQuery(ModelDefaultPolicyPo.class)
                        .eq(ModelDefaultPolicyPo::getDeleted, 0)
                        .orderByAsc(ModelDefaultPolicyPo::getScopeType)
                        .orderByAsc(ModelDefaultPolicyPo::getScopeId)
                        .orderByAsc(ModelDefaultPolicyPo::getModelType)
                        .orderByAsc(ModelDefaultPolicyPo::getProviderType))
                .stream()
                .map(ModelDefaultPolicyServiceImpl::toResp)
                .toList();
    }

    @Override
    @Transactional
    public ModelDefaultPolicyResp upsert(ModelDefaultPolicyReq req) {
        String scopeType = normalizeScopeType(req.getScopeType());
        String modelType = normalizeModelType(req.getModelType());
        String providerType = normalizeProviderType(scopeType, req.getProviderType());
        Long scopeId = normalizeScopeId(scopeType, req.getScopeId());
        requireEnabledModel(req.getModelConfigId(), modelType);

        ModelDefaultPolicyPo po = policyMapper.selectOne(Wrappers.lambdaQuery(ModelDefaultPolicyPo.class)
                .eq(ModelDefaultPolicyPo::getScopeType, scopeType)
                .eq(ModelDefaultPolicyPo::getScopeId, scopeId)
                .eq(ModelDefaultPolicyPo::getModelType, modelType)
                .eq(ModelDefaultPolicyPo::getProviderType, providerType)
                .eq(ModelDefaultPolicyPo::getDeleted, 0)
                .last("LIMIT 1"));
        if (po == null) {
            po = new ModelDefaultPolicyPo();
            po.setScopeType(scopeType);
            po.setScopeId(scopeId);
            po.setModelType(modelType);
            po.setProviderType(providerType);
            po.setModelConfigId(req.getModelConfigId());
            po.setEnabled(req.getEnabled() == null ? 1 : normalizeEnabled(req.getEnabled()));
            policyMapper.insert(po);
            return toResp(po);
        }
        po.setModelConfigId(req.getModelConfigId());
        po.setEnabled(req.getEnabled() == null ? po.getEnabled() : normalizeEnabled(req.getEnabled()));
        policyMapper.updateById(po);
        return toResp(po);
    }

    @Override
    public ModelDefaultPolicyResp resolve(String modelType, LlmCallContext context, String fallbackProviderType) {
        String normalizedModelType = normalizeModelType(modelType);
        if (context != null && context.getAppId() != null) {
            ModelDefaultPolicyPo app = findPolicy(SCOPE_APP, context.getAppId(), normalizedModelType, "");
            if (app != null) return toResp(app);
        }
        if (context != null && context.getProjectId() != null) {
            ModelDefaultPolicyPo project = findPolicy(SCOPE_PROJECT, context.getProjectId(), normalizedModelType, "");
            if (project != null) return toResp(project);
        }
        ModelDefaultPolicyPo global = findPolicy(SCOPE_GLOBAL, 0L, normalizedModelType, "");
        if (global != null) return toResp(global);

        String providerType = normalizeProviderType(fallbackProviderType);
        if (StringUtils.hasText(providerType)) {
            ModelDefaultPolicyPo providerFallback = findPolicy(SCOPE_PROVIDER_FALLBACK, 0L, normalizedModelType, providerType);
            if (providerFallback != null) return toResp(providerFallback);
        }
        return null;
    }

    private ModelDefaultPolicyPo findPolicy(String scopeType, Long scopeId, String modelType, String providerType) {
        ModelDefaultPolicyPo policy = policyMapper.selectOne(Wrappers.lambdaQuery(ModelDefaultPolicyPo.class)
                .eq(ModelDefaultPolicyPo::getScopeType, scopeType)
                .eq(ModelDefaultPolicyPo::getScopeId, scopeId)
                .eq(ModelDefaultPolicyPo::getModelType, modelType)
                .eq(ModelDefaultPolicyPo::getProviderType, providerType)
                .eq(ModelDefaultPolicyPo::getEnabled, 1)
                .eq(ModelDefaultPolicyPo::getDeleted, 0)
                .last("LIMIT 1"));
        if (policy == null) {
            return null;
        }
        ModelConfigPo modelConfig = modelConfigMapper.selectById(policy.getModelConfigId());
        if (modelConfig == null || !Integer.valueOf(1).equals(modelConfig.getEnabled())
                || !modelType.equals(normalizeModelType(modelConfig.getModelType()))) {
            return null;
        }
        return policy;
    }

    private void requireEnabledModel(Long modelConfigId, String modelType) {
        ModelConfigPo modelConfig = modelConfigMapper.selectById(modelConfigId);
        if (modelConfig == null || !Integer.valueOf(1).equals(modelConfig.getEnabled())) {
            throw new BizException(ErrorCode.NOT_FOUND, "默认模型配置不存在或已禁用: " + modelConfigId);
        }
        if (!modelType.equals(normalizeModelType(modelConfig.getModelType()))) {
            throw new BizException(ErrorCode.PARAM_ERROR, "默认模型类型与模型配置类型不一致");
        }
    }

    private static ModelDefaultPolicyResp toResp(ModelDefaultPolicyPo po) {
        ModelDefaultPolicyResp resp = new ModelDefaultPolicyResp();
        resp.setId(po.getId());
        resp.setScopeType(po.getScopeType());
        resp.setScopeId(po.getScopeId());
        resp.setModelType(po.getModelType());
        resp.setProviderType(po.getProviderType());
        resp.setModelConfigId(po.getModelConfigId());
        resp.setEnabled(po.getEnabled());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private static String normalizeScopeType(String scopeType) {
        String normalized = scopeType == null ? "" : scopeType.trim().toUpperCase();
        if (!List.of(SCOPE_GLOBAL, SCOPE_PROJECT, SCOPE_APP, SCOPE_PROVIDER_FALLBACK).contains(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "默认模型策略 scopeType 不合法");
        }
        return normalized;
    }

    private static String normalizeModelType(String modelType) {
        String normalized = modelType == null ? "" : modelType.trim().toUpperCase();
        if (!"CHAT".equals(normalized) && !"EMBEDDING".equals(normalized)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型类型只支持 CHAT / EMBEDDING");
        }
        return normalized;
    }

    private static Long normalizeScopeId(String scopeType, Long scopeId) {
        if (SCOPE_GLOBAL.equals(scopeType) || SCOPE_PROVIDER_FALLBACK.equals(scopeType)) {
            return 0L;
        }
        if (scopeId == null || scopeId <= 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "项目或应用默认模型策略必须指定 scopeId");
        }
        return scopeId;
    }

    private static String normalizeProviderType(String providerType) {
        return providerType == null ? "" : providerType.trim().toUpperCase();
    }

    private static String normalizeProviderType(String scopeType, String providerType) {
        String normalized = normalizeProviderType(providerType);
        if (SCOPE_PROVIDER_FALLBACK.equals(scopeType)) {
            if (!StringUtils.hasText(normalized)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "Provider fallback 默认模型策略必须指定 providerType");
            }
            return normalized;
        }
        return "";
    }

    private static int normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 0 ? 0 : 1;
    }
}
