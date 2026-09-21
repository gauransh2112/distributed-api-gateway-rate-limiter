package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter;

import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.redis.exception.RedisStorageException;
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
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisSlidingWindowCounterRateLimiter} with Redis mocked.
 *
 * <p>The weighted calculation itself happens inside the Lua script, so what these tests pin down is
 * the limiter's own contract: the two keys it derives, the weight it computes from the clock, the
 * TTL spanning two windows, and how the script's {@code {allowed, remaining}} reply maps onto a
 * decision. Redis semantics are covered by the integration tests.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Sliding Window Counter Rate Limiter — unit")
class RedisSlidingWindowCounterRateLimiterTest {

    /** 12:00:30 sits exactly halfway through a one-minute window, so the weight is 0.5. */
    private static final Instant BASE_TIME = Instant.parse("2026-09-21T12:00:30.000Z");
    private static final long CAPACITY = 10L;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final long CURRENT_WINDOW_EPOCH_SECOND =
            Instant.parse("2026-09-21T12:00:00.000Z").getEpochSecond();
    private static final long PREVIOUS_WINDOW_EPOCH_SECOND =
            Instant.parse("2026-09-21T11:59:00.000Z").getEpochSecond();

    @Mock
    private RedisService redisService;

    private RateLimiterProperties properties;
    private RedisSlidingWindowCounterRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(WINDOW);
        rateLimiter = limiterAt(BASE_TIME);
    }

    private RedisSlidingWindowCounterRateLimiter limiterAt(Instant instant) {
        return new RedisSlidingWindowCounterRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null,
                Clock.fixed(instant, ZoneOffset.UTC), redisService, new RedisKeyBuilder());
    }

    private RateLimitContext context(String clientId) {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", BASE_TIME, "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private void stubScript(long allowed, long remaining) {
        when(redisService.executeLua(eq(LuaScriptLoader.SLIDING_COUNTER_SCRIPT), eq(List.class), anyList(), anyList()))
                .thenReturn(List.of(allowed, remaining));
    }

    @SuppressWarnings("unchecked")
    private List<String> capture(int argumentIndex) {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        if (argumentIndex == 0) {
            verify(redisService).executeLua(any(), any(), captor.capture(), anyList());
        } else {
            verify(redisService).executeLua(any(), any(), anyList(), captor.capture());
        }
        return captor.getValue();
    }

    @Nested
    @DisplayName("1. Key derivation")
    class KeyDerivation {

        @Test
        @DisplayName("Passes the current and previous window keys, in that order")
        void testCurrentAndPreviousKeys() {
            stubScript(1L, 4L);

            rateLimiter.allowRequest(context("user-101"));

            assertThat(capture(0)).containsExactly(
                    "dev:ratelimiter:sliding:user-101:" + CURRENT_WINDOW_EPOCH_SECOND,
                    "dev:ratelimiter:sliding:user-101:" + PREVIOUS_WINDOW_EPOCH_SECOND);
        }

        @Test
        @DisplayName("The previous key is exactly one window earlier than the current key")
        void testPreviousKeyIsOneWindowBack() {
            stubScript(1L, 4L);

            rateLimiter.allowRequest(context("user-1"));

            List<String> keys = capture(0);
            long current = Long.parseLong(keys.get(0).substring(keys.get(0).lastIndexOf(':') + 1));
            long previous = Long.parseLong(keys.get(1).substring(keys.get(1).lastIndexOf(':') + 1));
            assertThat(current - previous).isEqualTo(WINDOW.toSeconds());
        }

        @Test
        @DisplayName("Distinct clients receive distinct key pairs")
        void testKeysArePerClient() {
            stubScript(1L, 4L);

            rateLimiter.allowRequest(context("client-a"));
            rateLimiter.allowRequest(context("client-b"));

            ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
            verify(redisService, org.mockito.Mockito.times(2))
                    .executeLua(any(), any(), keys.capture(), anyList());
            assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(1));
        }
    }

    @Nested
    @DisplayName("2. Script arguments")
    class ScriptArguments {

        @Test
        @DisplayName("Sends capacity, previous-window weight and a two-window TTL")
        void testArguments() {
            stubScript(1L, 4L);

            rateLimiter.allowRequest(context("user-1"));

            List<String> args = capture(1);
            assertThat(args).hasSize(3);
            assertThat(Long.parseLong(args.get(0))).isEqualTo(CAPACITY);
            // Halfway through the window, half of the previous window still overlaps.
            assertThat(Double.parseDouble(args.get(1))).isCloseTo(0.5, within(1e-9));
            // 2 x 60s window + 10s buffer: a counter is read as the previous window for a further
            // full window after its own ends.
            assertThat(Long.parseLong(args.get(2))).isEqualTo(130L);
        }

        @Test
        @DisplayName("Weight is 1.0 at the window start and decays to 0 at its end")
        void testWeightDecaysAcrossWindow() {
            stubScript(1L, 4L);

            limiterAt(Instant.parse("2026-09-21T12:00:00.000Z")).allowRequest(context("user-1"));
            limiterAt(Instant.parse("2026-09-21T12:00:45.000Z")).allowRequest(context("user-1"));
            limiterAt(Instant.parse("2026-09-21T12:00:59.999Z")).allowRequest(context("user-1"));

            ArgumentCaptor<List<String>> args = ArgumentCaptor.forClass(List.class);
            verify(redisService, org.mockito.Mockito.times(3))
                    .executeLua(any(), any(), anyList(), args.capture());
            assertThat(Double.parseDouble(args.getAllValues().get(0).get(1))).isCloseTo(1.0, within(1e-9));
            assertThat(Double.parseDouble(args.getAllValues().get(1).get(1))).isCloseTo(0.25, within(1e-9));
            assertThat(Double.parseDouble(args.getAllValues().get(2).get(1))).isLessThan(0.001);
        }

        @Test
        @DisplayName("TTL tracks the configured window duration")
        void testTtlTracksWindow() {
            properties.setDefaultWindow(Duration.ofSeconds(30));
            stubScript(1L, 4L);

            limiterAt(BASE_TIME).allowRequest(context("user-1"));

            assertThat(Long.parseLong(capture(1).get(2))).isEqualTo(70L);
        }
    }

    @Nested
    @DisplayName("3. Decision mapping")
    class DecisionMapping {

        @Test
        @DisplayName("An allowed reply carries the script's remaining count")
        void testAllowed() {
            stubScript(1L, 6L);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(CAPACITY);
            assertThat(decision.remainingRequests()).isEqualTo(6L);
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_ALLOWED);
        }

        @Test
        @DisplayName("A rejected reply produces reset time and retryAfter from the window boundary")
        void testRejected() {
            stubScript(0L, 0L);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.limit()).isEqualTo(CAPACITY);
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
            assertThat(decision.resetTime()).isEqualTo(Instant.parse("2026-09-21T12:01:00.000Z"));
            assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(30));
        }
    }

    @Nested
    @DisplayName("4. Edge cases and failure behaviour")
    class EdgeCases {

        @Test
        @DisplayName("Non-positive capacity rejects without contacting Redis")
        void testNonPositiveCapacityShortCircuits() {
            properties.setDefaultCapacity(0);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.limit()).isZero();
            verifyNoInteractions(redisService);
        }

        @Test
        @DisplayName("Non-positive window duration is rejected before any Redis call")
        void testInvalidWindowRejected() {
            properties.setDefaultWindow(Duration.ZERO);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("window duration must be strictly positive");
            verify(redisService, never()).executeLua(any(), any(), anyList(), anyList());
        }

        @Test
        @DisplayName("A null context is rejected")
        void testNullContextRejected() {
            assertThatThrownBy(() -> rateLimiter.allowRequest(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Redis failures propagate unchanged — no failure policy is applied here")
        void testRedisFailurePropagates() {
            when(redisService.executeLua(any(), any(), anyList(), anyList()))
                    .thenThrow(new RedisStorageException("EVALSHA", "dev:ratelimiter:sliding:user-1:1", "boom"));

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(RedisStorageException.class);
        }

        @Test
        @DisplayName("A malformed script reply is surfaced rather than silently allowed")
        void testMalformedReplyRejected() {
            when(redisService.executeLua(any(), any(), anyList(), anyList())).thenReturn(List.of(1L));

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable result");
        }

        @Test
        @DisplayName("A missing script reply is surfaced rather than silently allowed")
        void testNullReplyRejected() {
            when(redisService.executeLua(any(), any(), anyList(), anyList())).thenReturn(null);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable result");
        }

        @Test
        @DisplayName("Construction requires the Redis collaborators")
        void testConstructorValidation() {
            Clock clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
            assertThatThrownBy(() -> new RedisSlidingWindowCounterRateLimiter(
                    properties, null, null, clock, null, new RedisKeyBuilder()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RedisSlidingWindowCounterRateLimiter(
                    properties, null, null, clock, redisService, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
