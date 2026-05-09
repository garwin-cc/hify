package com.hify.model.api;

import lombok.Data;

import java.util.List;

/** 供应商详情：基础信息 + 模型列表 + 健康状态 */
@Data
public class ProviderDetailResp {
    private ProviderResp provider;
    private List<ModelConfigResp> modelConfigs;
    private ProviderHealthResp health;
}
