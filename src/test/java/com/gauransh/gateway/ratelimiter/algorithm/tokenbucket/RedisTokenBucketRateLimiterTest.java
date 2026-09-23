package com.gauransh.gateway.ratelimiter.algorithm.tokenbucket;

import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.model.RateLimitPolicy;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisTokenBucketRateLimiter} with Redis mocked.
 *
 * <p>Refill and consumption happen inside the Lua script, so these tests pin down the limiter's own
 * contract: the key it builds, the arguments it derives from the policy snapshot and clock, and how
 * the script's {@code {allowed, remaining, retryAfterMillis, tokens}} reply becomes a decision.
 * Bucket semantics are covered by the integration tests.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Token Bucket Rate Limiter — unit")
class RedisTokenBucketRateLimiterTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-23T12:00:30.000Z");
    private static final long BASE_MILLIS = BASE_TIME.toEpochMilli();
    private static final long CAPACITY = 10L;
    /** Tokens per second; 2.0/s is 0.002 tokens per millisecond. */
    private static final double REFILL_RATE = 2.0;

    @Mock
    private RedisService redisService;

    private RateLimiterProperties properties;
    private RedisTokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setRefillRate(REFILL_RATE);
        rateLimiter = newLimiter(null);
    }

    private RedisTokenBucketRateLimiter newLimiter(
            com.gauransh.gateway.ratelimiter.resolver.RateLimitPolicyResolver policyResolver) {
        return new RedisTokenBucketRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), policyResolver,
                Clock.fixed(BASE_TIME, ZoneOffset.UTC), redisService, new RedisKeyBuilder());
    }

    private RateLimitContext context(String clientId) {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", BASE_TIME, "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    /** Stubs the script reply. {@code tokens} is a string exactly as the script returns it. */
    private void stubScript(long allowed, long remaining, long retryAfterMillis, String tokens) {
        when(redisService.executeLua(eq(LuaScriptLoader.TOKEN_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                .thenReturn(List.of(allowed, remaining, retryAfterMillis, tokens));
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
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(context("user-101"));

            assertThat(captureKeys())
                    .as("the bucket is continuous, so the key carries no window segment")
                    .containsExactly("dev:ratelimiter:tokenbucket:user-101");
        }

        @Test
        @DisplayName("Client identifiers are lowercased by the key builder")
        void testKeyIsLowercased() {
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(context("USER-ABC"));

            assertThat(captureKeys()).containsExactly("dev:ratelimiter:tokenbucket:user-abc");
        }

        @Test
        @DisplayName("A blank client identifier falls back to the anonymous key")
        void testAnonymousFallback() {
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(new RateLimitContext("   ", "/api", "GET", BASE_TIME, "127.0.0.1", Map.of()));

            assertThat(captureKeys())
                    .containsExactly("dev:ratelimiter:tokenbucket:"
                            + RateLimitConstants.DEFAULT_ANONYMOUS_KEY.toLowerCase());
        }

        @Test
        @DisplayName("Two clients address two different buckets")
        void testClientsAreIsolated() {
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(context("client-a"));
            rateLimiter.allowRequest(context("client-b"));

            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
            verify(redisService, org.mockito.Mockito.times(2))
                    .executeLua(any(), any(), captor.capture(), anyList());
            assertThat(captor.getAllValues())
                    .containsExactly(List.of("dev:ratelimiter:tokenbucket:client-a"),
                            List.of("dev:ratelimiter:tokenbucket:client-b"));
        }
    }

    @Nested
    @DisplayName("2. Script arguments")
    class ScriptArguments {

        @Test
        @DisplayName("The Token Bucket script is invoked, not another algorithm's")
        void testCorrectScriptInvoked() {
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(context("user-101"));

            verify(redisService).executeLua(eq(LuaScriptLoader.TOKEN_BUCKET_SCRIPT), eq(List.class), anyList(), anyList());
        }

        @Test
        @DisplayName("Arguments are now, capacity, per-millisecond refill rate and TTL, in order")
        void testArgumentOrderAndValues() {
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(context("user-101"));

            assertThat(captureArgs()).containsExactly(
                    String.valueOf(BASE_MILLIS),
                    String.valueOf(CAPACITY),
                    String.valueOf(REFILL_RATE / 1000.0),
                    String.valueOf(RedisTokenBucketRateLimiter.BUCKET_TTL.toSeconds()));
        }

        @Test
        @DisplayName("The refill rate is converted from tokens/second to tokens/millisecond")
        void testRefillRateConvertedToPerMilli() {
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(context("user-101"));

            assertThat(Double.parseDouble(captureArgs().get(2)))
                    .as("2 tokens/second is 0.002 tokens/millisecond")
                    .isEqualTo(0.002d);
        }

        @Test
        @DisplayName("TTL is one hour, per the Redis design")
        void testTtlIsOneHour() {
            stubScript(1L, 9L, 0L, "9.0");

            rateLimiter.allowRequest(context("user-101"));

            assertThat(captureArgs().get(3)).isEqualTo("3600");
            assertThat(RedisTokenBucketRateLimiter.BUCKET_TTL).isEqualTo(Duration.ofHours(1));
        }

        @Test
        @DisplayName("A policy window overrides the configured refill rate")
        void testPolicyWindowDerivesRefillRate() {
            RateLimitPolicy policy = new RateLimitPolicy("policy-1", 20L, Duration.ofSeconds(10), 0L,
                    com.gauransh.gateway.ratelimiter.config.RateLimiterAlgorithm.TOKEN_BUCKET, true, 1, "PER_CLIENT");
            RedisTokenBucketRateLimiter limiter = newLimiter(ctx -> policy);
            stubScript(1L, 19L, 0L, "19.0");

            limiter.allowRequest(context("user-101"));

            List<String> args = captureArgs();
            assertThat(args.get(1)).as("capacity comes from the policy").isEqualTo("20");
            assertThat(Double.parseDouble(args.get(2)))
                    .as("20 tokens over 10 seconds is 2 tokens/second, so 0.002 per millisecond")
                    .isEqualTo(0.002d);
        }
    }

    @Nested
    @DisplayName("3. Decision mapping")
    class DecisionMapping {

        @Test
        @DisplayName("An admitted request reports capacity, remaining tokens and the allowed reason")
        void testAllowedDecision() {
            stubScript(1L, 7L, 0L, "7.5");

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(CAPACITY);
            assertThat(decision.remainingRequests()).isEqualTo(7L);
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_ALLOWED);
        }

        @Test
        @DisplayName("A rejected request carries retryAfter and the exceeded reason")
        void testRejectedDecision() {
            stubScript(0L, 0L, 500L, "0.0");

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.retryAfter()).isEqualTo(Duration.ofMillis(500));
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
        }

        @Test
        @DisplayName("A rejected request's reset time is now plus retryAfter")
        void testRejectedResetTime() {
            stubScript(0L, 0L, 500L, "0.0");

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.resetTime()).isEqualTo(BASE_TIME.plusMillis(500));
        }

        @Test
        @DisplayName("An admitted request's reset time is when the bucket would refill to full")
        void testAllowedResetTimeIsTimeToFull() {
            // 7.5 of 10 tokens left, refilling at 0.002 tokens/ms => 2.5 / 0.002 = 1250ms to full.
            stubScript(1L, 7L, 0L, "7.5");

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.resetTime()).isEqualTo(BASE_TIME.plusMillis(1250));
        }

        @Test
        @DisplayName("An admitted request reports no retryAfter")
        void testAllowedHasZeroRetryAfter() {
            stubScript(1L, 7L, 0L, "7.5");

            assertThat(rateLimiter.allowRequest(context("user-101")).retryAfter()).isEqualTo(Duration.ZERO);
        }
    }

    @Nested
    @DisplayName("4. Fractional token handling")
    class FractionalTokens {

        @Test
        @DisplayName("The exact fractional count drives the reset time, not the truncated remaining")
        void testFractionDrivesResetTime() {
            // remaining reports 7 whole tokens, but 7.9 are actually present: 2.1 / 0.002 = 1050ms.
            stubScript(1L, 7L, 0L, "7.9");

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.remainingRequests()).as("clients see whole tokens").isEqualTo(7L);
            assertThat(decision.resetTime())
                    .as("the reset time must use the exact count, or it would be 1000ms")
                    .isEqualTo(BASE_TIME.plusMillis(1050));
        }

        @Test
        @DisplayName("A token count returned as raw bytes is parsed rather than rejected")
        void testByteArrayTokenCountParsed() {
            when(redisService.executeLua(eq(LuaScriptLoader.TOKEN_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                    .thenReturn(List.of(1L, 7L, 0L, "7.5".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-101"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.resetTime()).isEqualTo(BASE_TIME.plusMillis(1250));
        }

        @Test
        @DisplayName("An unparseable token count fails loudly rather than silently mis-limiting")
        void testUnparseableTokenCountThrows() {
            stubScript(1L, 7L, 0L, "not-a-number");

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unparseable token count");
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
        @DisplayName("A non-positive refill rate throws and never contacts Redis")
        void testNonPositiveRefillRateThrows() {
            properties.setRefillRate(0.0);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Refill rate must be strictly positive");
            verifyNoInteractions(redisService);
        }

        @Test
        @DisplayName("Non-finite refill rates throw and never contact Redis")
        void testNonFiniteRefillRatesThrow() {
            for (double rate : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
                properties.setRefillRate(rate);
                assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                        .as("refill rate %s must be rejected", rate)
                        .isInstanceOf(IllegalArgumentException.class);
            }
            verifyNoInteractions(redisService);
        }

        @Test
        @DisplayName("A null script result fails loudly")
        void testNullResultThrows() {
            when(redisService.executeLua(eq(LuaScriptLoader.TOKEN_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                    .thenReturn(null);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable token bucket result");
        }

        @Test
        @DisplayName("A short script result fails loudly")
        void testShortResultThrows() {
            when(redisService.executeLua(eq(LuaScriptLoader.TOKEN_BUCKET_SCRIPT), eq(List.class), anyList(), anyList()))
                    .thenReturn(List.of(1L, 7L));

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-101")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable token bucket result");
        }

        @Test
        @DisplayName("Constructor arguments that would make the limiter unusable are rejected")
        void testConstructorNullChecks() {
            assertThatThrownBy(() -> new RedisTokenBucketRateLimiter(
                    properties, null, null, Clock.systemUTC(), null, new RedisKeyBuilder()))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("redisService");

            assertThatThrownBy(() -> new RedisTokenBucketRateLimiter(
                    properties, null, null, Clock.systemUTC(), redisService, null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("keyBuilder");
        }
    }
}
