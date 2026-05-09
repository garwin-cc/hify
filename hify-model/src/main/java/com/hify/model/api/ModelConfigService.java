package com.hify.model.api;

import java.util.List;

/** 模型配置服务，供跨模块调用（如 Agent 选择模型时的下拉数据）。 */
public interface ModelConfigService {

    /** 返回所有启用且未删除的模型配置，按 sort_order 升序。 */
    List<ModelConfigResp> listEnabled();

    /** 返回指定用途的启用模型配置，modelType=CHAT / EMBEDDING。 */
    List<ModelConfigResp> listEnabledByType(String modelType);

    /** 按 ID 获取模型配置，不存在时返回 null。 */
    ModelConfigResp getById(Long id);

    /** 手动新增模型配置。 */
    ModelConfigResp create(CreateModelConfigReq req);

    /** 更新模型用途。 */
    ModelConfigResp updateModelType(Long id, String modelType);

    /** 按 ID 列表批量查询，结果以 id → ModelConfigResp 的 Map 返回，供跨模块 N+1 优化使用。 */
    java.util.Map<Long, ModelConfigResp> mapByIds(List<Long> ids);
}
