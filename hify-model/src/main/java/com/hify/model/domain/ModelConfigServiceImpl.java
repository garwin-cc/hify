package com.hify.model.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.model.api.CreateModelConfigReq;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ModelConfigServiceImpl implements ModelConfigService {

    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;

    @Override
    public List<ModelConfigResp> listEnabled() {
        return modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>()
                        .eq(ModelConfigPo::getEnabled, 1)
                        .orderByAsc(ModelConfigPo::getSortOrder)
                        .orderByAsc(ModelConfigPo::getId)
        ).stream().map(ModelConfigServiceImpl::toResp).toList();
    }

    @Override
    public List<ModelConfigResp> listEnabledByType(String modelType) {
        String normalizedType = normalizeModelType(modelType);
        return modelConfigMapper.selectList(
                new LambdaQueryWrapper<ModelConfigPo>()
                        .eq(ModelConfigPo::getEnabled, 1)
                        .eq(ModelConfigPo::getModelType, normalizedType)
                        .orderByAsc(ModelConfigPo::getSortOrder)
                        .orderByAsc(ModelConfigPo::getId)
        ).stream().map(ModelConfigServiceImpl::toResp).toList();
    }

    @Override
    public ModelConfigResp getById(Long id) {
        ModelConfigPo po = modelConfigMapper.selectById(id);
        return po != null ? toResp(po) : null;
    }

    @Override
    @Transactional
    public ModelConfigResp create(CreateModelConfigReq req) {
        ProviderPo provider = providerMapper.selectById(req.getProviderId());
        if (provider == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "供应商不存在: " + req.getProviderId());
        }
        String name = req.getName() == null ? "" : req.getName().trim();
        String modelId = req.getModelId() == null ? "" : req.getModelId().trim();
        if (!StringUtils.hasText(name) || !StringUtils.hasText(modelId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型名称和模型 ID 不能为空");
        }
        long exists = modelConfigMapper.selectCount(new LambdaQueryWrapper<ModelConfigPo>()
                .eq(ModelConfigPo::getProviderId, req.getProviderId())
                .eq(ModelConfigPo::getModelId, modelId));
        if (exists > 0) {
            throw new BizException(ErrorCode.PARAM_ERROR, "该供应商下模型 ID 已存在: " + modelId);
        }

        ModelConfigPo po = new ModelConfigPo();
        po.setProviderId(req.getProviderId());
        po.setName(name);
        po.setModelId(modelId);
        po.setModelType(normalizeModelType(req.getModelType()));
        po.setContextSize(req.getContextSize() == null || req.getContextSize() <= 0
                ? 8192 : req.getContextSize());
        po.setExtraParams(Map.of());
        po.setEnabled(1);
        po.setSortOrder(0);
        modelConfigMapper.insert(po);
        return toResp(po);
    }

    @Override
    public ModelConfigResp updateModelType(Long id, String modelType) {
        ModelConfigPo po = modelConfigMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型配置不存在: " + id);
        }
        po.setModelType(normalizeModelType(modelType));
        modelConfigMapper.updateById(po);
        return toResp(po);
    }

    @Override
    public Map<Long, ModelConfigResp> mapByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        return modelConfigMapper.selectBatchIds(ids)
                .stream()
                .collect(Collectors.toMap(ModelConfigPo::getId, ModelConfigServiceImpl::toResp));
    }

    private static ModelConfigResp toResp(ModelConfigPo po) {
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

    private static String normalizeModelType(String modelType) {
        String normalizedType = modelType == null ? "" : modelType.trim().toUpperCase();
        if (!"CHAT".equals(normalizedType) && !"EMBEDDING".equals(normalizedType) && !"RERANK".equals(normalizedType)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "模型类型只支持 CHAT / EMBEDDING / RERANK");
        }
        return normalizedType;
    }
}
