package com.rick.frontendgen.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.Resource;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis 缓存管理器配置
 */
@Configuration
public class RedisCacheManagerConfig {

    @Resource
    private RedisConnectionFactory redisConnectionFactory;

    @Bean
    public CacheManager cacheManager() {
        GenericJackson2JsonRedisSerializer jsonSerializer = createJsonSerializer();

        // 默认配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30)) // 默认 30 分钟过期
                .disableCachingNullValues() // 禁用 null 值缓存
                // key 使用 String 序列化器
                //.serializeKeysWith(RedisSerializationContext.SerializationPair
                //        .fromSerializer(new StringRedisSerializer()));
                // value 使用带类型元数据的 JSON 序列化器
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                // 针对 good_app_page 配置5分钟过期
                .withCacheConfiguration("good_app_page",
                        defaultConfig.entryTtl(Duration.ofMinutes(5)))
                .build();
    }

    static GenericJackson2JsonRedisSerializer createJsonSerializer() {
        // 配置 ObjectMapper 支持 Java8 时间类型
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Redis 反序列化时拿不到方法的泛型返回类型，必须在 JSON 中保留实际类型信息。
        // 使用 builder 显式开启 default typing，避免缓存命中后被还原成 LinkedHashMap。
        return GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(objectMapper)
                .defaultTyping(true)
                .build();
    }
}
