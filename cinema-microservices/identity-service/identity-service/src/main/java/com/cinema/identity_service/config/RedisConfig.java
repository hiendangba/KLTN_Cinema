package com.cinema.identity_service.config;

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

    // ✅ Cache names CHỈ cho identity-service
    public static final String CACHE_BLACKLIST_TOKEN = "blacklist:token";
    public static final String CACHE_USER_SESSION = "user:session";
    public static final String CACHE_OTP = "otp"; // ✅ Thêm cho OTP

    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        RedisCacheConfiguration defaultConfig = createCacheConfig(
                Duration.ofHours(1),
                jsonSerializer
        );

        // ✅ CHỈ config cache cho identity-service
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
                CACHE_OTP,
                createCacheConfig(Duration.ofMinutes(5), jsonSerializer) // ✅ OTP 5 phút
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