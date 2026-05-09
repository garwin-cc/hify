package com.hify.common.util;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.web.PageResult;

import java.util.function.Function;

/**
 * 分页工具类，集中处理前端入参校验和 IPage 结果转换。
 *
 * <p>典型用法：
 * <pre>
 * // Controller 层接收前端参数并构造 Page 对象
 * Page{@literal <AgentPo>} pageParam = PageHelper.toPage(page, pageSize);
 * IPage{@literal <AgentPo>} result = agentMapper.selectPage(pageParam, wrapper);
 *
 * // 不需要类型转换时直接返回
 * return Result.ok(PageHelper.toPageResult(result));
 *
 * // 需要 PO → DTO 转换时
 * return Result.ok(PageHelper.toPageResult(result, converter::toDto));
 * </pre>
 */
public final class PageHelper {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private PageHelper() {
    }

    /**
     * 将前端传入的页码和每页条数转换为 MyBatis-Plus {@link Page} 对象。
     *
     * <ul>
     *   <li>page &lt; 1 时重置为 1</li>
     *   <li>pageSize &lt;= 0 时使用默认值 {@value DEFAULT_PAGE_SIZE}</li>
     *   <li>pageSize &gt; {@value MAX_PAGE_SIZE} 时截断为 {@value MAX_PAGE_SIZE}</li>
     * </ul>
     *
     * @param page     页码，从 1 开始
     * @param pageSize 每页条数
     * @param <T>      查询结果的 PO 类型
     */
    public static <T> Page<T> toPage(int page, int pageSize) {
        int safePage = Math.max(1, page);
        int safeSize = pageSize <= 0 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
        return new Page<>(safePage, safeSize);
    }

    /**
     * 将 MyBatis-Plus 查询结果直接转换为 {@link PageResult}，适用于列表元素类型不需要转换的场景。
     *
     * @param iPage MyBatis-Plus 分页查询结果
     * @param <T>   列表元素类型
     */
    public static <T> PageResult<T> toPageResult(IPage<T> iPage) {
        return PageResult.ofPage(iPage);
    }

    /**
     * 将 MyBatis-Plus 查询结果转换为 {@link PageResult}，并对每条记录应用转换函数（PO → DTO/VO）。
     *
     * @param iPage     MyBatis-Plus 分页查询结果
     * @param converter 类型转换函数，通常是 MapStruct Converter 的方法引用
     * @param <P>       原始 PO 类型
     * @param <T>       目标 DTO/VO 类型
     */
    public static <P, T> PageResult<T> toPageResult(IPage<P> iPage, Function<P, T> converter) {
        return PageResult.ofPage(iPage, converter);
    }
}
