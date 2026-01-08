package com.cinema.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    // Cache names
    public static final String CACHE_BLACKLIST_TOKEN = "blacklist:token";
    public static final String CACHE_USER_SESSION = "user:session";
    public static final String CACHE_MOVIE = "movie";
    public static final String CACHE_MOVIE_LIST = "movie:list";
    public static final String CACHE_CATEGORY = "category";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Serializer cho key
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Serializer cho value - KHÔNG CÓ THAM SỐ
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {

        // JSON serializer - KHÔNG CÓ THAM SỐ
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        // Cấu hình mặc định
        RedisCacheConfiguration defaultConfig = createCacheConfig(
                Duration.ofHours(1),
                jsonSerializer
        );

        // Cấu hình riêng cho từng cache
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        cacheConfigurations.put(
                CACHE_BLACKLIST_TOKEN,
                createCacheConfig(Duration.ofMinutes(30), jsonSerializer)
        );
        cacheConfigurations.put(
                CACHE_USER_SESSION,
                createCacheConfig(Duration.ofMinutes(30), jsonSerializer)
        );
        cacheConfigurations.put(
                CACHE_MOVIE,
                createCacheConfig(Duration.ofHours(2), jsonSerializer)
        );
        cacheConfigurations.put(
                CACHE_MOVIE_LIST,
                createCacheConfig(Duration.ofMinutes(30), jsonSerializer)
        );
        cacheConfigurations.put(
                CACHE_CATEGORY,
                createCacheConfig(Duration.ofDays(1), jsonSerializer)
        );

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    private RedisCacheConfiguration createCacheConfig(
            Duration ttl,
            RedisSerializer<Object> jsonSerializer) {

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer)
                )
                .disableCachingNullValues();
    }
}