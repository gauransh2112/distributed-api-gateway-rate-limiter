package com.gauransh.gateway.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.serializer.JacksonRedisSerializer;
import com.gauransh.gateway.redis.serializer.RedisSerializer;
import com.gauransh.gateway.redis.service.DefaultRedisService;
import com.gauransh.gateway.redis.service.RedisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Spring {@link Configuration} class for wiring Redis storage abstraction beans.
 */
@Configuration
public class RedisStorageConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RedisKeyBuilder redisKeyBuilder() {
        return new RedisKeyBuilder();
    }

    @Bean
    @ConditionalOnMissingBean
    public <T> RedisSerializer<T> redisSerializer(ObjectMapper objectMapper) {
        return new JacksonRedisSerializer<>(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisService redisService(StringRedisTemplate stringRedisTemplate) {
        return new DefaultRedisService(stringRedisTemplate);
    }
}
