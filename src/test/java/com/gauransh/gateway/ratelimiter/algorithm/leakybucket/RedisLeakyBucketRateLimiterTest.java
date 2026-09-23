package com.gauransh.gateway.ratelimiter.algorithm.leakybucket;

import com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.model.RateLimitPolicy;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.resolver.RateLimitPolicyResolver;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.script.LuaScriptLoader;
import com.gauransh.gateway.redis.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisLeakyBucketRateLimiter} with Redis mocked.
 *
 * <p>Draining and the admission test happen inside the Lua script, so these tests pin down the
 * limiter's own contract: the key it builds, the arguments it derives from the policy snapshot and
 * clock, and how the script's {@code {allowed, level, lastLeak}} reply becomes a decision. The
 * rounding rules are the interesting part here — Leaky Bucket reports remaining with {@code ceil}
 * and floors retryAfter at one millisecond, both differing from Token Bucket.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Leaky Bucket Rate Limiter — unit")
class RedisLeakyBucketRateLimiterTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-23T12:00:30.000Z");
    private static final long BASE_MILLIS = BASE_TIME.toEpochMilli();
    private static final long CAPACITY = 10L;
    /** Units per second; 2.0/s is 0.002 units per millisecond. */
    private static final double LEAK_RATE = 2.0;

    @Mock
    private RedisService redisService;

    private RateLimiterProperties properties;
    private RedisLeakyBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setRefillRate(LEAK_RATE);
        rateLimiter = newLimiter(null);
    }

    private RedisLeakyBucketRateLimiter newLimiter(RateLimitPolicyResolver policyResolver) {
        return new RedisLeakyBucketRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), policyResolver,
                Clock.fixed(BASE_TIME, ZoneOffset.UTC), redisService, new RedisKeyBuilder());
    }

    private RateLimitContext context(String clientId) {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", BASE_TIME, "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    /** Stubs the script reply. {@code level} is a string exactly as the script returns it. */
    private void stubScript(long allowed, String level, long lastLeak) {
        when(redisService.executeLua(eq(LuaScriptLoader.LEAKY_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                .thenReturn(List.of(allowed, level, lastLeak));
    }

    @SuppressWarnings("unchecked")
    private List<String> captureArgs() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(redisService).executeLua(any(), any(), anyList(), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<String> captureKeys() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(redisService).executeLua(any(), any(), captor.capture(), anyList());
        return captor.getValue();
    }

    @Nested
    @DisplayName("1. Key construction")
    class KeyConstruction {

        @Test
        @DisplayName("Key follows the Engineering Contract schema with no window component")
        void testKeyFollowsContractSchema() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(context("user-101"));

            assertThat(captureKeys()).containsExactly("dev:ratelimiter:leaky:user-101");
        }

        @Test
        @DisplayName("Client identifiers are lowercased by the key builder")
        void testKeyIsLowercased() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(context("USER-ABC"));

            assertThat(captureKeys()).containsExactly("dev:ratelimiter:leaky:user-abc");
        }

        @Test
        @DisplayName("A blank client identifier falls back to the anonymous key")
        void testAnonymousFallback() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(new RateLimitContext("  ", "/api", "GET", BASE_TIME, "127.0.0.1", Map.of()));

            assertThat(captureKeys()).containsExactly("dev:ratelimiter:leaky:"
                    + RateLimitConstants.DEFAULT_ANONYMOUS_KEY.toLowerCase());
        }

        @Test
        @DisplayName("Two clients address two different buckets")
        void testClientsAreIsolated() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(context("client-a"));
            rateLimiter.allowRequest(context("client-b"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(redisService, times(2)).executeLua(any(), any(), captor.capture(), anyList());
            assertThat(captor.getAllValues())
                    .containsExactly(List.of("dev:ratelimiter:leaky:client-a"),
                            List.of("dev:ratelimiter:leaky:client-b"));
        }
    }

    @Nested
    @DisplayName("2. Script arguments")
    class ScriptArguments {

        @Test
        @DisplayName("The Leaky Bucket script is invoked, not another algorithm's")
        void testCorrectScriptInvoked() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(context("user-101"));

            verify(redisService).executeLua(eq(LuaScriptLoader.LEAKY_BUCKET_SCRIPT), eq(List.class), anyList(), anyList());
        }

        @Test
        @DisplayName("Arguments are now, capacity, per-millisecond leak rate and TTL, in order")
        void testArgumentOrderAndValues() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(context("user-101"));

            assertThat(captureArgs()).containsExactly(
                    String.valueOf(BASE_MILLIS),
                    String.valueOf(CAPACITY),
                    String.valueOf(LEAK_RATE / 1000.0),
                    String.valueOf(RedisLeakyBucketRateLimiter.BUCKET_TTL.toSeconds()));
        }

        @Test
        @DisplayName("The leak rate is converted from units/second to units/millisecond")
        void testLeakRateConvertedToPerMilli() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(context("user-101"));

            assertThat(Double.parseDouble(captureArgs().get(2))).isEqualTo(0.002d);
        }

        @Test
        @DisplayName("TTL is one hour, per the Redis design")
        void testTtlIsOneHour() {
            stubScript(1L, "1.0", BASE_MILLIS);

            rateLimiter.allowRequest(context("user-101"));

            assertThat(captureArgs().get(3)).isEqualTo("3600");
            assertThat(RedisLeakyBucketRateLimiter.BUCKET_TTL).isEqualTo(Duration.ofHours(1));
        }

        @Test
        @DisplayName("A policy window overrides the configured leak rate")
        void testPolicyWindowDerivesLeakRate() {
            RateLimitPolicy policy = new RateLimitPolicy("policy-1", 20L, Duration.ofSeconds(10), 0L,
                    RateLimiterAlgorithm.LEAKY_BUCKET, true, 1, "PER_CLIENT");
            RedisLeakyBucketRateLimiter limiter = newLimiter(ctx -> policy);
            stubScript(1L, "1.0", BASE_MILLIS);

            limiter.allowRequest(context("user-101"));

            List<String> args = captureArgs();
            assertThat(args.get(1)).as("capacity comes from the policy").isEqualTo("20");
            assertThat(Double.parseDouble(args.get(2)))
                    .as("20 units over 10 seconds is 2/second, so 0.002 per millisecond")
                    .isEqualTo(0.002d);
        }
    }

    @Nested
    @DisplayName("3. Decision mapping and rounding")
    class DecisionMapping {

        @Test
        @DisplayName("An admitted request reports capacity and the allowed reason")
        void testAllowedDecision() {
            stubScript(1L, "3.0", BASE_MILLIS);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(CAPACITY);
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_ALLOWED);
            assertThat(decision.retryAfter()).isEqualTo(Duration.ZERO);
        }

        @Test
        @DisplayName("Remaining rounds the water level UP, unlike Token Bucket which truncates")
        void testRemainingUsesCeiling() {
            // 3.2 units of water in a capacity-10 bucket leaves 10 - ceil(3.2) = 6, not 7.
            stubScript(1L, "3.2", BASE_MILLIS);

            assertThat(rateLimiter.allowRequest(context("user-101")).remainingRequests())
                    .as("a partially used unit still occupies its slot")
                    .isEqualTo(6L);
        }

        @Test
        @DisplayName("An admitted request's reset time is when the bucket would be empty")
        void testAllowedResetTimeIsTimeToEmpty() {
            // 3.0 units draining at 0.002/ms => 1500ms to empty.
            stubScript(1L, "3.0", BASE_MILLIS);

            assertThat(rateLimiter.allowRequest(context("user-101")).resetTime())
                    .isEqualTo(BASE_TIME.plusMillis(1500));
        }

        @Test
        @DisplayName("The reset time is measured from the stored timestamp, not from now")
        void testResetTimeUsesStoredTimestamp() {
            // Under a backward clock the script preserves an older, larger lastLeak; the reset time
            // must follow it rather than the observed clock.
            long preservedLastLeak = BASE_MILLIS + 5_000L;
            stubScript(1L, "3.0", preservedLastLeak);

            assertThat(rateLimiter.allowRequest(context("user-101")).resetTime())
                    .isEqualTo(Instant.ofEpochMilli(preservedLastLeak + 1500L));
        }

        @Test
        @DisplayName("A rejected request carries retryAfter and the exceeded reason")
        void testRejectedDecision() {
            // Level 10 in a capacity-10 bucket: one unit must drain, 1 / 0.002 = 500ms.
            stubScript(0L, "10.0", BASE_MILLIS);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.retryAfter()).isEqualTo(Duration.ofMillis(500));
            assertThat(decision.resetTime()).isEqualTo(BASE_TIME.plusMillis(500));
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
        }

        @Test
        @DisplayName("retryAfter never reports zero, even when the shortfall is vanishingly small")
        void testRetryAfterIsFlooredAtOneMillisecond() {
            // Level 9.0001 needs only 0.0001 units to drain — under a millisecond of leak time.
            stubScript(0L, "9.0001", BASE_MILLIS);

            assertThat(rateLimiter.allowRequest(context("user-101")).retryAfter())
                    .as("a rejected client must never be told to retry immediately")
                    .isEqualTo(Duration.ofMillis(1));
        }
    }

    @Nested
    @DisplayName("4. Fractional level handling")
    class FractionalLevels {

        @Test
        @DisplayName("A level returned as raw bytes is parsed rather than rejected")
        void testByteArrayLevelParsed() {
            when(redisService.executeLua(eq(LuaScriptLoader.LEAKY_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                    .thenReturn(List.of(1L, "3.0".getBytes(StandardCharsets.UTF_8), BASE_MILLIS));

            assertThat(rateLimiter.allowRequest(context("user-101")).resetTime())
                    .isEqualTo(BASE_TIME.plusMillis(1500));
        }

        @Test
        @DisplayName("An unparseable level fails loudly rather than silently mis-limiting")
        void testUnparseableLevelThrows() {
            stubScript(1L, "not-a-number", BASE_MILLIS);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unparseable water level");
        }
    }

    @Nested
    @DisplayName("5. Edge cases and validation")
    class EdgeCases {

        @Test
        @DisplayName("A null context is rejected")
        void testNullContextThrows() {
            assertThatThrownBy(() -> rateLimiter.allowRequest(null))
                    .isInstanceOf(NullPointerException.class);
            verifyNoInteractions(redisService);
        }

        @Test
        @DisplayName("Non-positive capacity throws and never contacts Redis")
        void testNonPositiveCapacityThrows() {
            properties.setDefaultCapacity(0L);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("capacity must be strictly positive");
            verifyNoInteractions(redisService);
        }

        @Test
        @DisplayName("A non-positive leak rate throws and never contacts Redis")
        void testNonPositiveLeakRateThrows() {
            properties.setRefillRate(0.0);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Leak rate must be strictly positive");
            verifyNoInteractions(redisService);
        }

        @Test
        @DisplayName("Non-finite leak rates throw and never contact Redis")
        void testNonFiniteLeakRatesThrow() {
            for (double rate : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                properties.setRefillRate(rate);
                assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                        .as("leak rate %s must be rejected", rate)
                        .isInstanceOf(IllegalArgumentException.class);
            }
            verifyNoInteractions(redisService);
        }

        @Test
        @DisplayName("A null script result fails loudly")
        void testNullResultThrows() {
            when(redisService.executeLua(eq(LuaScriptLoader.LEAKY_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                    .thenReturn(null);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable leaky bucket result");
        }

        @Test
        @DisplayName("A short script result fails loudly")
        void testShortResultThrows() {
            when(redisService.executeLua(eq(LuaScriptLoader.LEAKY_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                    .thenReturn(List.of(1L, "1.0"));

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable leaky bucket result");
        }

        @Test
        @DisplayName("Constructor arguments that would make the limiter unusable are rejected")
        void testConstructorNullChecks() {
            assertThatThrownBy(() -> new RedisLeakyBucketRateLimiter(
                    properties, null, null, Clock.systemUTC(), null, new RedisKeyBuilder()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("redisService");

            assertThatThrownBy(() -> new RedisLeakyBucketRateLimiter(
                    properties, null, null, Clock.systemUTC(), redisService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("keyBuilder");
        }
    }
}
