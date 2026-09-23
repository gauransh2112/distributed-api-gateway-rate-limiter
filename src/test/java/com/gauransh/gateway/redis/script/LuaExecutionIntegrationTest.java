package com.gauransh.gateway.redis.script;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.redis.exception.LuaExecutionException;
import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.service.RedisService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Live integration test for the Lua execution engine.
 *
 * <p>Verifies real {@code SCRIPT LOAD} / {@code EVALSHA} behaviour, atomic counter-plus-TTL
 * semantics, automatic recovery from a Redis-side script cache miss, and Lua failure translation.</p>
 *
 * <p>Executes against Redis on port 6379 when available. Uses {@link EnabledIf} so that offline
 * unit test execution remains 100% independent of Redis.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
class LuaExecutionIntegrationTest {

    @Autowired
    private LuaExecutor luaExecutor;

    @Autowired
    private LuaScriptLoader luaScriptLoader;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private String testKey;

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @AfterEach
    void tearDown() {
        if (testKey != null) {
            redisService.delete(testKey);
            testKey = null;
        }
    }

    @Test
    @DisplayName("Should register the increment script with the Redis script cache during startup")
    void testScriptRegisteredAtStartup() {
        LuaScript script = luaScriptLoader.getScript(LuaScriptLoader.INCREMENT_SCRIPT);

        assertThat(script.getSha()).hasSize(40);
        assertThat(scriptExists(script.getSha())).isTrue();
    }

    @Test
    @DisplayName("Should increment a counter atomically through EVALSHA and return the updated value")
    void testIncrementViaEvalSha() {
        testKey = luaKey();

        Long first = luaExecutor.executeLua(
                LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("1", "60"));
        Long second = luaExecutor.executeLua(
                LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("5", "60"));

        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(6L);
        assertThat(redisService.get(testKey)).contains("6");
    }

    @Test
    @DisplayName("Should apply the TTL inside the script without extending an existing expiration")
    void testTtlAppliedInsideScriptAndNotExtended() {
        testKey = luaKey();

        luaExecutor.executeLua(LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("1", "60"));
        Optional<Duration> afterFirst = redisService.getTtl(testKey);

        luaExecutor.executeLua(LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("1", "600"));
        Optional<Duration> afterSecond = redisService.getTtl(testKey);

        assertThat(afterFirst).isPresent();
        assertThat(afterFirst.get()).isBetween(Duration.ofSeconds(50), Duration.ofSeconds(60));
        assertThat(afterSecond).isPresent();
        assertThat(afterSecond.get()).isLessThanOrEqualTo(Duration.ofSeconds(60));
    }

    @Test
    @DisplayName("Should leave the key persistent when the script is invoked with a zero TTL")
    void testZeroTtlLeavesKeyPersistent() {
        testKey = luaKey();

        luaExecutor.executeLua(LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("3", "0"));

        assertThat(redisService.get(testKey)).contains("3");
        assertThat(redisService.getTtl(testKey)).isEmpty();
    }

    @Test
    @DisplayName("Should recover automatically from a Redis script cache miss and still execute")
    void testNoScriptRecoveryAgainstLiveRedis() {
        testKey = luaKey();
        luaExecutor.executeLua(LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("1", "60"));

        flushScriptCache();
        assertThat(scriptExists(luaScriptLoader.getScript(LuaScriptLoader.INCREMENT_SCRIPT).getSha())).isFalse();

        Long value = luaExecutor.executeLua(
                LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("1", "60"));

        assertThat(value).isEqualTo(2L);
        assertThat(scriptExists(luaScriptLoader.getScript(LuaScriptLoader.INCREMENT_SCRIPT).getSha())).isTrue();
    }

    @Test
    @DisplayName("Should translate a Lua argument validation failure into REDIS-005")
    void testInvalidArgumentsTranslatedToLuaExecutionException() {
        testKey = luaKey();

        assertThatThrownBy(() -> luaExecutor.executeLua(
                LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("not-a-number", "60")))
                .isInstanceOf(LuaExecutionException.class)
                .hasMessageContaining("Lua script [increment.lua] execution failed")
                .extracting(e -> ((LuaExecutionException) e).getErrorCode())
                .isEqualTo("REDIS-005");

        assertThat(redisService.exists(testKey)).isFalse();
    }

    @Test
    @DisplayName("Should raise REDIS-006 for a script that is not shipped with the Gateway")
    void testUnknownScriptRaisesNotLoaded() {
        // Deliberately a name the Gateway will never ship. Using the name of a real algorithm's
        // script here would stop testing "unknown script" the moment that algorithm is added.
        assertThatThrownBy(() -> luaExecutor.executeLua(
                "no_such_script.lua", Long.class, List.of(luaKey()), List.of("1")))
                .isInstanceOf(LuaScriptNotLoadedException.class)
                .extracting(e -> ((LuaScriptNotLoadedException) e).getErrorCode())
                .isEqualTo("REDIS-006");
    }

    @Test
    @DisplayName("Should expose Lua execution through the RedisService contract")
    void testExecuteLuaThroughRedisService() {
        testKey = luaKey();

        Long value = redisService.executeLua(
                LuaScriptLoader.INCREMENT_SCRIPT, Long.class, List.of(testKey), List.of("4", "60"));

        assertThat(value).isEqualTo(4L);
        assertThat(redisService.getTtl(testKey)).isPresent();
    }

    private String luaKey() {
        return keyBuilder.buildKey("ratelimiter", "lua", UUID.randomUUID().toString());
    }

    private boolean scriptExists(String sha) {
        List<Boolean> result = redisTemplate.execute(
                (RedisCallback<List<Boolean>>) connection -> connection.scriptingCommands().scriptExists(sha));
        return result != null && !result.isEmpty() && Boolean.TRUE.equals(result.get(0));
    }

    private void flushScriptCache() {
        redisTemplate.execute((RedisCallback<Void>) connection -> {
            connection.scriptingCommands().scriptFlush();
            return null;
        });
    }
}
