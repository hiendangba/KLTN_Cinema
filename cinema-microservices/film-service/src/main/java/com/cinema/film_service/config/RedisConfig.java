package com.cinema.film_service.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    // Cache names constants
    public static final String CACHE_BLACKLIST_TOKEN = "blacklist:token";
    public static final String CACHE_USER_SESSION = "user:session";
    public static final String CACHE_OTP = "otp";

    @Value("${spring.data.redis.host}")
    private String redisHost;

    @Value("${spring.data.redis.port}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.username:default}")
    private String redisUsername;

    @Value("${spring.data.redis.timeout:10000}")
    private long timeout;

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        log.info("Configuring Redis connection to {}:{}", redisHost, redisPort);

        // Redis standalone configuration
        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
        redisConfig.setHostName(redisHost);
        redisConfig.setPort(redisPort);
        redisConfig.setUsername(redisUsername);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            redisConfig.setPassword(redisPassword);
        }

        SocketOptions socketOptions = SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(timeout))
                .keepAlive(true)
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .socketOptions(socketOptions)
                .autoReconnect(true)
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeout)))
                .build();

        // Lettuce client configuration
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
                .clientOptions(clientOptions)
                .commandTimeout(Duration.ofMillis(timeout))
                .build();

        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig, clientConfig);
        factory.setValidateConnection(true);
        factory.setShareNativeConnection(true);

        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        log.info("Configuring RedisTemplate");
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
    @Override
    public CacheManager cacheManager() {
        log.info("Configuring CacheManager");

        RedisSerializer<Object> jsonSerializer = RedisSerializer.json();

        RedisCacheConfiguration defaultConfig = createCacheConfig(
                Duration.ofHours(1),
                jsonSerializer
        );

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
                createCacheConfig(Duration.ofMinutes(5), jsonSerializer)
        );

        return RedisCacheManager.builder(redisConnectionFactory())
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .transactionAware()
                .build();
    }

    /**
     * Helper method - GIỮ NGUYÊN CODE CŨ
     */
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

    /**
     * BỔ SUNG: Error handler cho cache
     * Khi Redis down, app vẫn chạy được thay vì crash
     */
    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception,
                                            org.springframework.cache.Cache cache,
                                            Object key) {
                log.error("Failed to get cache [{}] with key [{}]: {}",
                        cache.getName(), key, exception.getMessage());
                // Không throw exception - let the method execute normally
            }

            @Override
            public void handleCachePutError(RuntimeException exception,
                                            org.springframework.cache.Cache cache,
                                            Object key,
                                            Object value) {
                log.error("Failed to put cache [{}] with key [{}]: {}",
                        cache.getName(), key, exception.getMessage());
                // Không throw exception - method continues
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception,
                                              org.springframework.cache.Cache cache,
                                              Object key) {
                log.error("Failed to evict cache [{}] with key [{}]: {}",
                        cache.getName(), key, exception.getMessage());
                // Không throw exception
            }

            @Override
            public void handleCacheClearError(RuntimeException exception,
                                              org.springframework.cache.Cache cache) {
                log.error("Failed to clear cache [{}]: {}",
                        cache.getName(), exception.getMessage());
                // Không throw exception
            }
        };
    }
}