package com.gauransh.gateway.redis.key;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisKeyBuilderTest {

    private final RedisKeyBuilder keyBuilder = new RedisKeyBuilder();

    @Test
    @DisplayName("Should build key with default environment 'dev'")
    void testBuildKeyDefaultEnvironment() {
        String key = keyBuilder.buildKey("ratelimiter", "user", "123");
        assertThat(key).isEqualTo("dev:ratelimiter:user:123");
    }

    @Test
    @DisplayName("Should enforce lowercase formatting on all key components")
    void testBuildKeyLowercaseEnforcement() {
        String key = keyBuilder.buildKey("PROD", "RateLimiter", "USER", "Client-ABC");
        assertThat(key).isEqualTo("prod:ratelimiter:user:client-abc");
    }

    @Test
    @DisplayName("Should allow explicit environment prefix")
    void testBuildKeyExplicitEnvironment() {
        String prodKey = keyBuilder.buildKey("prod", "ratelimiter", "user", "123");
        assertThat(prodKey).isEqualTo("prod:ratelimiter:user:123");

        String devMetricsKey = keyBuilder.buildKey("dev", "gateway", "metrics", "active");
        assertThat(devMetricsKey).isEqualTo("dev:gateway:metrics:active");
    }

    @ParameterizedTest
    @CsvSource({
            "null, module, resource, id",
            "dev, '', resource, id",
            "dev, module, '   ', id",
            "dev, module, resource, ''"
    })
    @DisplayName("Should throw IllegalArgumentException when any key component is null or blank")
    void testBuildKeyInvalidComponents(String env, String module, String resource, String identifier) {
        String environment = "null".equals(env) ? null : env;
        assertThatThrownBy(() -> keyBuilder.buildKey(environment, module, resource, identifier))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
