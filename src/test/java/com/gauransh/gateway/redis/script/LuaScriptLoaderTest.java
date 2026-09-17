package com.gauransh.gateway.redis.script;

import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LuaScriptLoader} covering classpath loading, SHA computation,
 * Redis registration, and startup resilience.
 *
 * <p>Redis is mocked: script loading must remain verifiable without infrastructure.</p>
 */
@ExtendWith(MockitoExtension.class)
class LuaScriptLoaderTest {


    @Mock
    private StringRedisTemplate redisTemplate;

    private LuaScriptLoader loader;

    @BeforeEach
    void setUp() {
        loader = new LuaScriptLoader(redisTemplate, List.of(LuaScriptLoader.INCREMENT_SCRIPT));
    }

    @Test
    @DisplayName("Should reject construction without at least one script name")
    void testConstructorValidation() {
        assertThatThrownBy(() -> new LuaScriptLoader(redisTemplate, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one Lua script name");

        assertThatThrownBy(() -> new LuaScriptLoader(null, List.of("increment.lua")))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Should load the script body from the classpath and register it with Redis")
    void testInitializeLoadsAndRegisters() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("sha-from-redis");

        loader.initialize();

        LuaScript script = loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT);
        assertThat(script.getName()).isEqualTo(LuaScriptLoader.INCREMENT_SCRIPT);
        assertThat(script.getBody()).contains("INCRBY");
        assertThat(script.getSha()).isEqualTo("sha-from-redis");
        verify(redisTemplate).execute(any(RedisCallback.class));
    }

    @Test
    @DisplayName("Should compute the SHA1 digest of the script body when Redis returns no SHA")
    void testShaComputedLocallyMatchesScriptBody() throws Exception {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn(null);

        loader.initialize();

        LuaScript script = loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT);
        assertThat(script.getSha()).isEqualTo(expectedSha(LuaScriptLoader.INCREMENT_SCRIPT));
        assertThat(script.getSha()).hasSize(40);
    }

    @Test
    @DisplayName("Should keep startup resilient when Redis is unavailable during registration")
    void testInitializeToleratesRedisFailure() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenThrow(new QueryTimeoutException("Redis unavailable"));

        loader.initialize();

        LuaScript script = loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT);
        assertThat(script.getBody()).contains("INCRBY");
        assertThat(script.getSha()).isEqualTo(expectedShaQuietly(LuaScriptLoader.INCREMENT_SCRIPT));
    }

    @Test
    @DisplayName("Should fail fast when a configured script is missing from the classpath")
    void testInitializeFailsForMissingScriptResource() {
        LuaScriptLoader missing = new LuaScriptLoader(redisTemplate, List.of("does_not_exist.lua"));

        assertThatThrownBy(missing::initialize)
                .isInstanceOf(LuaScriptNotLoadedException.class)
                .hasMessageContaining("does_not_exist.lua")
                .hasMessageContaining("not found on classpath");
    }

    @Test
    @DisplayName("Should raise REDIS-006 when requesting a script that was never loaded")
    void testGetScriptUnknownName() {
        lenient().when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("sha");
        loader.initialize();

        assertThatThrownBy(() -> loader.getScript("token_bucket.lua"))
                .isInstanceOf(LuaScriptNotLoadedException.class)
                .hasMessageContaining("token_bucket.lua")
                .extracting(e -> ((LuaScriptNotLoadedException) e).getErrorCode())
                .isEqualTo("REDIS-006");
    }

    @Test
    @DisplayName("Should reject null or blank script names")
    void testScriptNameValidation() {
        assertThatThrownBy(() -> loader.getScript(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null or blank");

        assertThatThrownBy(() -> loader.getScript("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should re-register the script with Redis and refresh the cached SHA on reload")
    void testReloadRefreshesSha() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenReturn("sha-initial")
                .thenReturn("sha-reloaded");
        loader.initialize();

        LuaScript reloaded = loader.reload(LuaScriptLoader.INCREMENT_SCRIPT);

        assertThat(reloaded.getSha()).isEqualTo("sha-reloaded");
        assertThat(loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT).getSha()).isEqualTo("sha-reloaded");
        verify(redisTemplate, times(2)).execute(any(RedisCallback.class));
    }

    @Test
    @DisplayName("Should raise REDIS-006 when Redis rejects registration during reload")
    void testReloadFailurePropagatesAsNotLoaded() {
        when(redisTemplate.execute(any(RedisCallback.class)))
                .thenReturn("sha-initial")
                .thenThrow(new QueryTimeoutException("Redis unavailable"));
        loader.initialize();

        assertThatThrownBy(() -> loader.reload(LuaScriptLoader.INCREMENT_SCRIPT))
                .isInstanceOf(LuaScriptNotLoadedException.class)
                .hasMessageContaining("Redis rejected script registration")
                .hasCauseInstanceOf(QueryTimeoutException.class);
    }

    @Test
    @DisplayName("Should never expose the script body through diagnostic output")
    void testToStringExcludesScriptBody() {
        when(redisTemplate.execute(any(RedisCallback.class))).thenReturn("sha-value");
        loader.initialize();

        String diagnostics = loader.getScript(LuaScriptLoader.INCREMENT_SCRIPT).toString();

        assertThat(diagnostics).contains(LuaScriptLoader.INCREMENT_SCRIPT).contains("sha-value");
        assertThat(diagnostics).doesNotContain("INCRBY");
    }

    private String expectedShaQuietly(String scriptName) {
        try {
            return expectedSha(scriptName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String expectedSha(String scriptName) throws Exception {
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream(LuaScriptLoader.SCRIPT_BASE_PATH + scriptName)) {
            assertThat(in).isNotNull();
            byte[] body = in.readAllBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(new String(body, StandardCharsets.UTF_8)
                    .getBytes(StandardCharsets.UTF_8)));
        }
    }
}
