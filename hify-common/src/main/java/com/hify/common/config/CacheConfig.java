package com.hify.common.config;

import com.hify.common.cache.CacheNames;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final String KEY_PREFIX = "hify:";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory,
                                          GenericJackson2JsonRedisSerializer jsonSerializer) {
        RedisCacheConfiguration defaults = defaultConfig(Duration.ofMinutes(30), jsonSerializer);

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                CacheNames.PROVIDER, defaultConfig(Duration.ofMinutes(30), jsonSerializer),
                CacheNames.MODEL, defaultConfig(Duration.ofMinutes(30), jsonSerializer),
                CacheNames.AGENT_DETAIL, defaultConfig(Duration.ofMinutes(10), jsonSerializer),
                CacheNames.AGENT_LIST, defaultConfig(Duration.ofMinutes(10), jsonSerializer),
                CacheNames.SESSION, defaultConfig(Duration.ofHours(2), jsonSerializer),
                CacheNames.MCP_TOOL, defaultConfig(Duration.ofMinutes(10), jsonSerializer),
                CacheNames.WORKFLOW, defaultConfig(Duration.ofMinutes(10), jsonSerializer),
                CacheNames.KNOWLEDGE_CONFIG, defaultConfig(Duration.ofMinutes(10), jsonSerializer),
                CacheNames.PERMISSION, defaultConfig(Duration.ofMinutes(5), jsonSerializer)
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaults)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }

    private RedisCacheConfiguration defaultConfig(Duration ttl,
                                                   GenericJackson2JsonRedisSerializer jsonSerializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .prefixCacheNameWith(KEY_PREFIX)
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(jsonSerializer));
    }
}
