package com.gauransh.gateway.redis.script;

import com.gauransh.gateway.redis.exception.LuaExecutionException;
import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DefaultLuaExecutor} covering delegation, input validation,
 * script cache miss recovery, and exception translation.
 *
 * <p>Redis is mocked: these tests verify the execution engine's behaviour, not Redis semantics.</p>
 */
@ExtendWith(MockitoExtension.class)
class DefaultLuaExecutorTest {

    private static final String SCRIPT = LuaScriptLoader.INCREMENT_SCRIPT;
    private static final List<String> KEYS = List.of("dev:ratelimiter:user:1");
    private static final List<String> ARGS = List.of("1", "60");

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private LuaScriptLoader scriptLoader;

    private DefaultLuaExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new DefaultLuaExecutor(redisTemplate, scriptLoader);
    }

    @Test
    @DisplayName("Should reject construction without a template or a script loader")
    void testConstructorValidation() {
        assertThatThrownBy(() -> new DefaultLuaExecutor(null, scriptLoader))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DefaultLuaExecutor(redisTemplate, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should execute the cached script via EVALSHA and return its result")
    void testExecuteDelegatesToRedis() {
        when(scriptLoader.getScript(SCRIPT)).thenReturn(script("sha-1"));
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(5L);

        Long result = executor.executeLua(SCRIPT, Long.class, KEYS, ARGS);

        assertThat(result).isEqualTo(5L);
        verify(redisTemplate).execute(any(RedisCallback.class));
        verify(scriptLoader, never()).reload(SCRIPT);
    }

    @Test
    @DisplayName("Should reload the script and retry once on a Redis script cache miss")
    void testNoScriptRecovery() {
        when(scriptLoader.getScript(SCRIPT)).thenReturn(script("stale-sha"));
        when(scriptLoader.reload(SCRIPT)).thenReturn(script("fresh-sha"));
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RedisSystemException("NOSCRIPT No matching script. Please use EVAL.",
                        new IllegalStateException("NOSCRIPT No matching script")))
                .thenReturn(9L);

        Long result = executor.executeLua(SCRIPT, Long.class, KEYS, ARGS);

        assertThat(result).isEqualTo(9L);
        verify(scriptLoader, times(1)).reload(SCRIPT);
        verify(redisTemplate, times(2)).execute(any(RedisCallback.class));
    }

    @Test
    @DisplayName("Should detect a script cache miss reported by a driver specific exception type")
    void testNoScriptRecoveryByExceptionType() {
        when(scriptLoader.getScript(SCRIPT)).thenReturn(script("stale-sha"));
        when(scriptLoader.reload(SCRIPT)).thenReturn(script("fresh-sha"));
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RedisSystemException("script error", new RedisNoScriptTestException()))
                .thenReturn(3L);

        assertThat(executor.executeLua(SCRIPT, Long.class, KEYS, ARGS)).isEqualTo(3L);
        verify(scriptLoader).reload(SCRIPT);
    }

    @Test
    @DisplayName("Should raise REDIS-005 when the retry after a script reload also fails")
    void testNoScriptRecoveryRetryFailure() {
        when(scriptLoader.getScript(SCRIPT)).thenReturn(script("stale-sha"));
        when(scriptLoader.reload(SCRIPT)).thenReturn(script("fresh-sha"));
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new RedisSystemException("NOSCRIPT No matching script", new IllegalStateException("NOSCRIPT")))
                .thenThrow(new QueryTimeoutException("Redis command timed out"));

        assertThatThrownBy(() -> executor.executeLua(SCRIPT, Long.class, KEYS, ARGS))
                .isInstanceOf(LuaExecutionException.class)
                .hasMessageContaining("retry after script reload failed")
                .extracting(e -> ((LuaExecutionException) e).getErrorCode())
                .isEqualTo("REDIS-005");
    }

    @Test
    @DisplayName("Should translate a Lua runtime failure into REDIS-005 without retrying")
    void testExecutionFailureTranslation() {
        when(scriptLoader.getScript(SCRIPT)).thenReturn(script("sha-1"));
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new InvalidDataAccessApiUsageException("ERR user_script:1: attempt to compare nil"));

        assertThatThrownBy(() -> executor.executeLua(SCRIPT, Long.class, KEYS, ARGS))
                .isInstanceOf(LuaExecutionException.class)
                .hasMessageContaining("Lua script [increment.lua] execution failed")
                .hasCauseInstanceOf(InvalidDataAccessApiUsageException.class);

        verify(scriptLoader, never()).reload(SCRIPT);
    }

    @Test
    @DisplayName("Should propagate REDIS-006 when the script was never loaded")
    void testUnknownScriptPropagates() {
        when(scriptLoader.getScript("token_bucket.lua"))
                .thenThrow(new LuaScriptNotLoadedException("token_bucket.lua", "not registered"));

        assertThatThrownBy(() -> executor.executeLua("token_bucket.lua", Long.class, KEYS, ARGS))
                .isInstanceOf(LuaScriptNotLoadedException.class);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("Should validate script name, result type, keys, and arguments before touching Redis")
    void testInputValidation() {
        lenient().when(scriptLoader.getScript(SCRIPT)).thenReturn(script("sha-1"));

        assertThatThrownBy(() -> executor.executeLua(null, Long.class, KEYS, ARGS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");

        assertThatThrownBy(() -> executor.executeLua("  ", Long.class, KEYS, ARGS))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> executor.executeLua(SCRIPT, null, KEYS, ARGS))
                .isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> executor.executeLua(SCRIPT, Long.class, null, ARGS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("keys must not be null");

        assertThatThrownBy(() -> executor.executeLua(SCRIPT, Long.class, KEYS, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("args must not be null");

        assertThatThrownBy(() -> executor.executeLua(SCRIPT, Long.class, Arrays.asList("k", null), ARGS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain null values");

        verifyNoInteractions(redisTemplate);
    }

    private LuaScript script(String sha) {
        return new LuaScript(SCRIPT, "return redis.call('INCRBY', KEYS[1], ARGV[1])", sha);
    }

    /**
     * Stands in for the driver's dedicated script cache miss exception type, whose simple name
     * carries the {@code NoScript} marker.
     */
    private static final class RedisNoScriptTestException extends RuntimeException {
        private RedisNoScriptTestException() {
            super("script not present in cache");
        }
    }
}
