package com.hify.model.api;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ProviderListItemResp {
    private Long id;
    private String name;
    private String type;
    private String baseUrl;
    private Integer enabled;
    private Integer sortOrder;
    private LocalDateTime createdAt;
    /** UP / DOWN / DEGRADED / UNKNOWN / null（从未检测） */
    private String healthStatus;
    private Integer latencyMs;
    /** 已启用的模型数量 */
    private int modelCount;
    /** 该供应商下所有模型（含禁用），用于前端展开列表 */
    private List<ModelConfigResp> models;
}
