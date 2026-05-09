package com.hify.common.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置。
 *
 * <p><b>序列化策略：</b>
 * <ul>
 *   <li>key / hashKey：{@link StringRedisSerializer}，Redis CLI 可直接读取</li>
 *   <li>value / hashValue：{@link GenericJackson2JsonRedisSerializer}，JSON 格式，
 *       包含 {@code @class} 字段以支持复杂类型（POJO、List、Map）的反序列化</li>
 * </ul>
 *
 * <p><b>注意：</b>此处为 Redis 专用 {@link ObjectMapper}，独立于全局 HTTP 序列化的 ObjectMapper。
 * 不能合并为同一个 Bean——{@code activateDefaultTyping} 会在 JSON 中写入 {@code @class}，
 * 若用于 HTTP 响应将暴露内部类名，影响安全性与接口稳定性。
 *
 * <p><b>连接池、超时、密码等配置在 application.yml 中声明：</b>
 * <pre>
 * spring:
 *   data:
 *     redis:
 *       host: localhost
 *       port: 6379
 *       password: ""
 *       lettuce:
 *         pool:
 *           max-active: 16
 *           max-idle: 8
 *           min-idle: 2
 *           max-wait: 1000ms
 * </pre>
 */
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = redisJsonSerializer();

        // key、hashKey 用字符串：便于 Redis CLI 查看，也方便按前缀批量删除
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // value、hashValue 用 JSON：保留类型信息，反序列化时能还原原始 Java 类型
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * 构造 Redis 专用 ObjectMapper。
     *
     * <p>{@code activateDefaultTyping} 在序列化结果中写入 {@code "@class":"com.hify.xxx.Foo"}，
     * 反序列化时 Jackson 根据此字段还原正确的 Java 类型，无需在 get 时手动指定 Class。
     * 代价：缓存中的 JSON 与 Java 类名绑定，重命名类后旧缓存条目无法反序列化（需清空或设短 TTL）。
     */
    /** 供 RedisTemplate 和 RedisCacheManager 共用，避免 LocalDateTime 序列化不一致 */
    @Bean
    public GenericJackson2JsonRedisSerializer redisJsonSerializer() {
        return buildJsonSerializer();
    }

    private GenericJackson2JsonRedisSerializer buildJsonSerializer() {
        ObjectMapper mapper = new ObjectMapper();

        // LocalDateTime 等 Java 8 时间类型序列化为 ISO-8601 字符串，而非时间戳数组
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // 允许序列化所有可见性的字段（包含 private），不依赖 getter
        mapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);

        // 写入 @class 类型元数据，NON_FINAL 覆盖大多数业务 POJO
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return new GenericJackson2JsonRedisSerializer(mapper);
    }
}
