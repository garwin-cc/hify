package com.hify.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 所有业务 PO 的公共基类。子类只需声明业务字段，无需重复定义通用字段。
 *
 * <p><b>约定（来自 CLAUDE.md 数据库规范）：</b>
 * <ul>
 *   <li>主键 {@code id}：BIGINT 自增，禁止 UUID</li>
 *   <li>时间字段：{@code LocalDateTime} 对应 {@code DATETIME(3)}，由 {@link HifyMetaObjectHandler} 自动填充</li>
 *   <li>逻辑删除：{@code deleted=0} 正常，{@code deleted=1} 已删除，由 {@code @TableLogic} 驱动，
 *       查询时自动追加 {@code AND deleted = 0}，禁止手写</li>
 *   <li>{@code createdBy}：创建人 ID，由业务层在保存前从安全上下文中填入（Auth 模块实现后补充到 Handler）</li>
 * </ul>
 *
 * <p><b>子类写法：</b>
 * <pre>
 * {@literal @}Data
 * {@literal @}TableName("t_agent")
 * {@literal @}EqualsAndHashCode(callSuper = false)
 * public class AgentPo extends BaseEntity {
 *     private String name;
 *     private String systemPrompt;
 *     // ...
 * }
 * </pre>
 */
@Getter
@Setter
public abstract class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键，数据库自增，业务代码禁止手动赋值 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 创建时间，精度 DATETIME(3)。
     * INSERT 时由 {@link HifyMetaObjectHandler#insertFill} 自动填充，禁止业务代码赋值。
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /**
     * 最后更新时间。
     * INSERT 和 UPDATE 时均由 {@link HifyMetaObjectHandler} 自动填充。
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    /**
     * 创建人 ID。
     * 当前阶段（无 Auth 模块）依赖数据库 DEFAULT 0；
     * Auth 模块接入后，在 {@link HifyMetaObjectHandler#insertFill} 中从 SecurityContext 填充。
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createdBy;

    /**
     * 逻辑删除标志：0=正常，1=已删除。
     * {@code @TableLogic} 使 MyBatis-Plus 在所有查询中自动追加 {@code AND deleted = 0}，
     * 在删除操作中执行 {@code UPDATE ... SET deleted = 1}。
     * 禁止在 Mapper XML 或 Wrapper 中手写 deleted 条件。
     */
    @TableLogic
    private Integer deleted;
}
