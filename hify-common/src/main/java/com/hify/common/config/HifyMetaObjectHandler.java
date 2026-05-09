package com.hify.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 *
 * <p>触发条件：Entity 字段上声明了 {@code @TableField(fill = FieldFill.INSERT)}
 * 或 {@code @TableField(fill = FieldFill.INSERT_UPDATE)}。
 *
 * <p>{@code strict} 系列方法只在字段值为 {@code null} 时填充，不会覆盖业务代码的显式赋值，
 * 比 {@code setFieldValByName} 更安全。
 *
 * <p><b>待扩展（Auth 模块接入后）：</b>
 * 在 {@link #insertFill} 中从 Spring Security {@code SecurityContextHolder} 取当前用户 ID，
 * 填充 {@code createdBy}：
 * <pre>
 *     Long userId = SecurityUtil.currentUserId();  // 待实现
 *     this.strictInsertFill(metaObject, "createdBy", Long.class, userId);
 * </pre>
 */
@Slf4j
@Component
public class HifyMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createdAt",  LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt",  LocalDateTime.class, now);
        // Auth 模块接入后替换为 SecurityContext.currentUserId()
        this.strictInsertFill(metaObject, "createdBy",  Long.class, 0L);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
    }
}
