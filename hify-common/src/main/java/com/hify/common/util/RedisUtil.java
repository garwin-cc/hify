package com.hify.common.util;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Redis 操作工具类。
 *
 * <p>对 {@link RedisTemplate} 的薄封装，屏蔽 ops 对象的获取和 null 返回值处理。
 * 仅封装高频基础操作；复杂场景（Pipeline、Lua 脚本、HyperLogLog）直接注入 RedisTemplate 使用。
 *
 * <p><b>Key 命名规范（来自 CLAUDE.md）：</b>
 * <pre>
 *   {module}:{resource}:{id}          → agent:config:42
 *   {module}:{resource}:{id}:{field}  → conv:context:abc123:messages
 * </pre>
 *
 * <p><b>TTL 规范：</b>
 * <ul>
 *   <li>Provider/Agent 配置：30 分钟</li>
 *   <li>会话上下文：2 小时</li>
 *   <li>热点数据：根据业务场景设置，禁止永不过期</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class RedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;

    // ------------------------------------------------------------------ set

    /** 写入缓存（无过期时间，谨慎使用）。 */
    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /** 写入缓存并设置 TTL。 */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    // ------------------------------------------------------------------ get

    /** 读取缓存，缺失时返回 null。 */
    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * 读取缓存并转换为指定类型。
     *
     * <p>依赖序列化时写入的 {@code @class} 元数据完成类型还原，
     * 当缓存内容类型与 {@code type} 不符时抛出 {@link ClassCastException}，
     * 调用方需确保写入与读取使用相同的 Java 类型。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return type.cast(value);
    }

    // ------------------------------------------------------------------ delete

    /** 删除单个 key，key 不存在时无副作用。 */
    public Boolean delete(String key) {
        return redisTemplate.delete(key);
    }

    /** 批量删除，返回实际删除的 key 数量。 */
    public Long delete(Collection<String> keys) {
        return redisTemplate.delete(keys);
    }

    // ------------------------------------------------------------------ expire / ttl

    /**
     * 设置或刷新 key 的过期时间。
     *
     * @return {@code true} 操作成功；{@code false} key 不存在或设置失败
     */
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        return redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 查询剩余过期时间（秒）。
     *
     * @return 剩余秒数；{@code -1} 表示未设置过期；{@code -2} 表示 key 不存在
     */
    public Long getExpire(String key) {
        return redisTemplate.getExpire(key, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ exists

    /** 判断 key 是否存在。 */
    public Boolean hasKey(String key) {
        return redisTemplate.hasKey(key);
    }
}
