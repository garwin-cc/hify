package com.hify.model.api;

import com.hify.common.web.PageResult;

public interface ProviderService {

    PageResult<ProviderListItemResp> list(ProviderQuery query);

    ProviderDetailResp getById(Long id);

    ProviderResp create(CreateProviderReq req);

    ProviderResp update(Long id, UpdateProviderReq req);

    void delete(Long id);

    /** 切换启用 / 禁用状态 */
    ProviderResp toggle(Long id);

    /** 对指定供应商发起连通性测试，结果同步写入 t_provider_health */
    ConnectivityTestResult test(Long providerId);
}
