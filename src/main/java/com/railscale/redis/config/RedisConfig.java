package com.railscale.redis.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration class for configuring Redis templates and connection.
 * Conditionally enabled based on the {@code spring.data.redis.enabled} property.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = true)
public class RedisConfig {

    /**
     * Creates and configures a {@link RedisTemplate} with String serializers for keys and values.
     *
     * @param connectionFactory the Redis connection factory
     * @return configured RedisTemplate instance
     */
    @Bean
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Creates a {@link StringRedisTemplate} bean for simplified String-based Redis operations.
     *
     * @param connectionFactory the Redis connection factory
     * @return StringRedisTemplate instance
     */
    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
