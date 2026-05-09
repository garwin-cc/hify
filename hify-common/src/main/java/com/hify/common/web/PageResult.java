package com.hify.common.web;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.util.List;
import java.util.function.Function;

/**
 * 分页响应体。data 字段包含 records + 分页元信息，前端通过统一拦截器解包后得到 PageData 对象。
 *
 * <p>前端收到的 JSON 结构：
 * <pre>
 * {
 *   "code": 200,
 *   "message": "ok",
 *   "data": {
 *     "records": [...],
 *     "total": 100,
 *     "page": 1,
 *     "size": 20
 *   }
 * }
 * </pre>
 *
 * <p>典型用法：
 * <pre>
 *   // 直接从 MyBatis-Plus IPage 转换（同类型，无需转换）
 *   return PageResult.ofPage(poPage);
 *
 *   // 带类型转换（PO → VO）
 *   return PageResult.ofPage(poPage, converter::toVo);
 *
 *   // 手动构造
 *   return PageResult.ok(list, total, page, size);
 * </pre>
 *
 * @param <T> 列表元素类型（Controller 层的 VO/DTO，禁止暴露 PO）
 */
public class PageResult<T> extends Result<PageResult.PageData<T>> {

    private PageResult() {
        super();
    }

    private PageResult(PageData<T> pageData) {
        super(200, "ok", pageData);
    }

    // ------------------------------------------------------------------ 工厂方法

    public static <T> PageResult<T> ok(List<T> records, long total, int page, int size) {
        return new PageResult<>(new PageData<>(records, total, page, size));
    }

    public static <T> PageResult<T> ofPage(IPage<T> page) {
        return new PageResult<>(new PageData<>(
                page.getRecords(),
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize()
        ));
    }

    public static <P, T> PageResult<T> ofPage(IPage<P> page, Function<P, T> converter) {
        List<T> records = page.getRecords().stream().map(converter).toList();
        return new PageResult<>(new PageData<>(
                records,
                page.getTotal(),
                (int) page.getCurrent(),
                (int) page.getSize()
        ));
    }

    // ------------------------------------------------------------------ 嵌套数据类

    @Data
    public static class PageData<T> {
        private final List<T> records;
        private final long total;
        private final int page;
        private final int size;
    }
}
