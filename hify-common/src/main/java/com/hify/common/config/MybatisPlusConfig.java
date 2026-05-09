package com.hify.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 核心配置。
 *
 * <p>逻辑删除的全局默认值（与 {@code @TableLogic} 配合）在 {@code application.yml} 中声明：
 * <pre>
 * mybatis-plus:
 *   global-config:
 *     db-config:
 *       logic-delete-value: 1       # deleted=1 表示已删除
 *       logic-not-delete-value: 0   # deleted=0 表示正常
 *   configuration:
 *     map-underscore-to-camel-case: true   # created_at → createdAt（默认开启，显式声明）
 *     log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl   # dev 环境输出 SQL
 * </pre>
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * MyBatis-Plus 拦截器链。
     *
     * <p>注意：拦截器的添加顺序即执行顺序，分页插件须在乐观锁插件之前。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 1. 分页插件
        interceptor.addInnerInterceptor(paginationInterceptor());

        // 2. 乐观锁插件（对应 Entity 字段上的 @Version，用于 Agent 配置的并发更新）
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());

        return interceptor;
    }

    private PaginationInnerInterceptor paginationInterceptor() {
        PaginationInnerInterceptor interceptor = new PaginationInnerInterceptor(DbType.MYSQL);

        // overflow=false：请求页码超出总页数时返回空列表，不自动跳转到最后一页
        // 与 CLAUDE.md 分页规范一致：超出限制时由业务层返回错误，不静默处理
        interceptor.setOverflow(false);

        // 单次查询上限 1000 条，防止一次性拉取全量数据撑爆内存
        // 超出限制时 MyBatis-Plus 抛出 TooManyResultsException
        interceptor.setMaxLimit(1000L);

        return interceptor;
    }
}
