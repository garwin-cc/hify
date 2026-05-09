package com.hify.model.api;

import lombok.Data;

@Data
public class ProviderQuery {

    /** 按供应商类型过滤，null 表示不过滤 */
    private String type;

    /** 按启用状态过滤：1=启用 0=禁用，null 表示不过滤 */
    private Integer enabled;

    /** 页码，从 1 开始，默认 1 */
    private int page = 1;

    /** 每页条数，默认 20，最大 100（由 PageHelper 截断） */
    private int pageSize = 20;
}
