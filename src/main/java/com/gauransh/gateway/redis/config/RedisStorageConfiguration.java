package com.gauransh.gateway.redis.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.serializer.JacksonRedisSerializer;
import com.gauransh.gateway.redis.script.DefaultLuaExecutor;
import com.gauransh.gateway.redis.script.LuaExecutor;
import com.gauransh.gateway.redis.script.LuaScriptLoader;
import com.gauransh.gateway.redis.serializer.RedisSerializer;
import com.gauransh.gateway.redis.service.DefaultRedisService;
import com.gauransh.gateway.redis.service.RedisService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

/**
 * Spring {@link Configuration} class for wiring Redis storage abstraction and Lua execution beans.
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

    /**
     * Loads every Lua script shipped with the Gateway and registers it with Redis at startup.
     *
     * <p>Script bodies are read from the classpath eagerly. Registration with Redis is attempted
     * here so that steady-state traffic executes {@code EVALSHA} against an already populated
     * script cache.</p>
     */
    @Bean
    @ConditionalOnMissingBean
    public LuaScriptLoader luaScriptLoader(StringRedisTemplate stringRedisTemplate) {
        LuaScriptLoader loader = new LuaScriptLoader(
                stringRedisTemplate,
                List.of(LuaScriptLoader.INCREMENT_SCRIPT, LuaScriptLoader.SLIDING_COUNTER_SCRIPT));
        loader.initialize();
        return loader;
    }

    @Bean
    @ConditionalOnMissingBean
    public LuaExecutor luaExecutor(StringRedisTemplate stringRedisTemplate, LuaScriptLoader luaScriptLoader) {
        return new DefaultLuaExecutor(stringRedisTemplate, luaScriptLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public RedisService redisService(StringRedisTemplate stringRedisTemplate, LuaExecutor luaExecutor) {
        return new DefaultRedisService(stringRedisTemplate, luaExecutor);
    }
}
