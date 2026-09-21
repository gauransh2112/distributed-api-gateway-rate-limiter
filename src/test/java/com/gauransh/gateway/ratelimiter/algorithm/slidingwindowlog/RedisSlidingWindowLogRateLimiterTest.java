package com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog;

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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisSlidingWindowLogRateLimiter} with Redis mocked.
 *
 * <p>Eviction and counting happen inside the Lua script, so these tests pin down the limiter's own
 * contract: the key it builds, the cutoff and TTL it derives from the clock, the uniqueness of the
 * member token, and how the script's {@code {allowed, remaining, oldest}} reply becomes a decision.
 * Redis semantics are covered by the integration tests.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Sliding Window Log Rate Limiter — unit")
class RedisSlidingWindowLogRateLimiterTest {

    private static final Instant BASE_TIME = Instant.parse("2026-09-21T12:00:30.000Z");
    private static final long CAPACITY = 5L;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock
    private RedisService redisService;

    private RateLimiterProperties properties;
    private RedisSlidingWindowLogRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(WINDOW);
        rateLimiter = limiterWithTokens(new AtomicInteger()::getAndIncrement);
    }

    private RedisSlidingWindowLogRateLimiter limiterWithTokens(java.util.function.Supplier<Integer> counter) {
        return new RedisSlidingWindowLogRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null,
                Clock.fixed(BASE_TIME, ZoneOffset.UTC), redisService, new RedisKeyBuilder(),
                () -> "token-" + counter.get());
    }

    private RateLimitContext context(String clientId) {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", BASE_TIME, "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private void stubScript(long allowed, long remaining, long oldestMillis) {
        when(redisService.executeLua(eq(LuaScriptLoader.SLIDING_LOG_SCRIPT), eq(List.class), anyList(), anyList()))
                .thenReturn(List.of(allowed, remaining, oldestMillis));
    }

    @SuppressWarnings("unchecked")
    private List<String> captureArgs() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(redisService).executeLua(any(), any(), anyList(), captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("1. Key construction")
    class KeyConstruction {

        @Test
        @DisplayName("Key follows the contract schema and carries no window component")
        void testKeyFormat() {
            stubScript(1L, 4L, BASE_TIME.toEpochMilli());
            ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("user-101"));

            verify(redisService).executeLua(any(), any(), keys.capture(), anyList());
            assertThat(keys.getValue()).containsExactly("dev:ratelimiter:log:user-101");
        }

        @Test
        @DisplayName("The key is stable across requests — one continuous log per client")
        void testKeyStableAcrossRequests() {
            stubScript(1L, 4L, BASE_TIME.toEpochMilli());
            ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("user-1"));
            rateLimiter.allowRequest(context("user-1"));

            verify(redisService, times(2)).executeLua(any(), any(), keys.capture(), anyList());
            assertThat(keys.getAllValues().get(0)).isEqualTo(keys.getAllValues().get(1));
        }

        @Test
        @DisplayName("Distinct clients receive distinct logs")
        void testKeysArePerClient() {
            stubScript(1L, 4L, BASE_TIME.toEpochMilli());
            ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("client-a"));
            rateLimiter.allowRequest(context("client-b"));

            verify(redisService, times(2)).executeLua(any(), any(), keys.capture(), anyList());
            assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(1));
        }
    }

    @Nested
    @DisplayName("2. Script arguments")
    class ScriptArguments {

        @Test
        @DisplayName("Sends now, cutoff, capacity, TTL and a member token")
        void testArguments() {
            stubScript(1L, 4L, BASE_TIME.toEpochMilli());

            rateLimiter.allowRequest(context("user-1"));

            List<String> args = captureArgs();
            assertThat(args).hasSize(5);
            assertThat(Long.parseLong(args.get(0))).isEqualTo(BASE_TIME.toEpochMilli());
            // Cutoff is exactly one window behind now; the script treats it as an exclusive bound.
            assertThat(Long.parseLong(args.get(1)))
                    .isEqualTo(BASE_TIME.minus(WINDOW).toEpochMilli());
            assertThat(Long.parseLong(args.get(2))).isEqualTo(CAPACITY);
            // Window + 10s buffer. One continuous log, so no doubling as in Sliding Window Counter.
            assertThat(Long.parseLong(args.get(3))).isEqualTo(70L);
            assertThat(args.get(4)).isNotBlank();
        }

        @Test
        @DisplayName("TTL tracks the configured window duration")
        void testTtlTracksWindow() {
            properties.setDefaultWindow(Duration.ofSeconds(30));
            stubScript(1L, 4L, BASE_TIME.toEpochMilli());

            rateLimiter.allowRequest(context("user-1"));

            assertThat(Long.parseLong(captureArgs().get(3))).isEqualTo(40L);
        }

        @Test
        @DisplayName("Every request carries a distinct member token — the collision guard")
        void testMemberTokenIsUniquePerRequest() {
            stubScript(1L, 4L, BASE_TIME.toEpochMilli());
            ArgumentCaptor<List<String>> args = ArgumentCaptor.forClass(List.class);

            for (int i = 0; i < 25; i++) {
                rateLimiter.allowRequest(context("user-1"));
            }

            verify(redisService, times(25)).executeLua(any(), any(), anyList(), args.capture());
            Set<String> members = new HashSet<>();
            args.getAllValues().forEach(a -> members.add(a.get(4)));
            assertThat(members)
                    .as("identical members would collide in the sorted set and lose requests")
                    .hasSize(25);
        }

        @Test
        @DisplayName("The default member token source produces unique values")
        void testDefaultTokenSourceIsUnique() {
            RedisSlidingWindowLogRateLimiter defaultLimiter = new RedisSlidingWindowLogRateLimiter(
                    properties, new DefaultRateLimitKeyResolver(), null,
                    Clock.fixed(BASE_TIME, ZoneOffset.UTC), redisService, new RedisKeyBuilder());
            stubScript(1L, 4L, BASE_TIME.toEpochMilli());
            ArgumentCaptor<List<String>> args = ArgumentCaptor.forClass(List.class);

            for (int i = 0; i < 25; i++) {
                defaultLimiter.allowRequest(context("user-1"));
            }

            verify(redisService, times(25)).executeLua(any(), any(), anyList(), args.capture());
            Set<String> members = new HashSet<>();
            args.getAllValues().forEach(a -> members.add(a.get(4)));
            assertThat(members).hasSize(25);
        }
    }

    @Nested
    @DisplayName("3. Decision mapping")
    class DecisionMapping {

        @Test
        @DisplayName("An allowed reply carries the script's remaining count")
        void testAllowed() {
            stubScript(1L, 3L, BASE_TIME.toEpochMilli());

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(CAPACITY);
            assertThat(decision.remainingRequests()).isEqualTo(3L);
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_ALLOWED);
        }

        @Test
        @DisplayName("Reset time is the oldest entry plus one window, not the wall clock")
        void testResetTimeDerivesFromOldestEntry() {
            long oldest = BASE_TIME.minusSeconds(20).toEpochMilli();
            stubScript(0L, 0L, oldest);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.resetTime()).isEqualTo(Instant.ofEpochMilli(oldest).plus(WINDOW));
            // Oldest entry ages out 40s after now, so that is how long the caller must wait.
            assertThat(decision.retryAfter()).isEqualTo(Duration.ofSeconds(40));
        }

        @Test
        @DisplayName("An empty log falls back to now plus one window for the reset time")
        void testEmptyLogFallsBackToDefaultResetTime() {
            stubScript(0L, 0L, -1L);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.resetTime()).isEqualTo(BASE_TIME.plus(WINDOW));
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
            assertThat(decision.resetTime()).isEqualTo(BASE_TIME.plus(WINDOW));
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
                    .thenThrow(new RedisStorageException("EVALSHA", "dev:ratelimiter:log:user-1", "boom"));

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(RedisStorageException.class);
        }

        @Test
        @DisplayName("A malformed or missing script reply is surfaced rather than silently allowed")
        void testUnusableReplyRejected() {
            when(redisService.executeLua(any(), any(), anyList(), anyList())).thenReturn(List.of(1L, 2L));
            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable result");

            when(redisService.executeLua(any(), any(), anyList(), anyList())).thenReturn(null);
            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("unusable result");
        }

        @Test
        @DisplayName("Construction requires the Redis collaborators and a token source")
        void testConstructorValidation() {
            Clock clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
            assertThatThrownBy(() -> new RedisSlidingWindowLogRateLimiter(
                    properties, null, null, clock, null, new RedisKeyBuilder()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RedisSlidingWindowLogRateLimiter(
                    properties, null, null, clock, redisService, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RedisSlidingWindowLogRateLimiter(
                    properties, null, null, clock, redisService, new RedisKeyBuilder(), null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
