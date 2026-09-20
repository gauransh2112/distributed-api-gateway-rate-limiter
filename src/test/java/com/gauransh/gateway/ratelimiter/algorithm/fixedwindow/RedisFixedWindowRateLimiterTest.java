package com.gauransh.gateway.ratelimiter.algorithm.fixedwindow;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RedisFixedWindowRateLimiter} with Redis mocked.
 *
 * <p>Redis semantics are not the subject here — those are covered by the integration tests. What
 * these tests pin down is the limiter's own contract: the key it builds, the script and arguments
 * it sends, the TTL it computes, and how a returned counter value maps onto a decision.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Redis Fixed Window Rate Limiter — unit")
class RedisFixedWindowRateLimiterTest {

    /** 2026-09-20T12:00:30Z — deliberately mid-window so window-start truncation is observable. */
    private static final Instant BASE_TIME = Instant.parse("2026-09-20T12:00:30.000Z");
    private static final long CAPACITY = 5L;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    @Mock
    private RedisService redisService;

    private RateLimiterProperties properties;
    private Clock clock;
    private RedisFixedWindowRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(WINDOW);
        clock = Clock.fixed(BASE_TIME, ZoneOffset.UTC);
        rateLimiter = new RedisFixedWindowRateLimiter(
                properties, new DefaultRateLimitKeyResolver(), null, clock, redisService, new RedisKeyBuilder());
    }

    private RateLimitContext context(String clientId) {
        return new RateLimitContext(clientId, "/api/v1/resource", "GET", BASE_TIME, "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(clientId)));
    }

    private void stubCounter(long value) {
        when(redisService.executeLua(eq(LuaScriptLoader.INCREMENT_SCRIPT), eq(Long.class), anyList(), anyList()))
                .thenReturn(value);
    }

    @Nested
    @DisplayName("1. Redis key construction")
    class KeyConstruction {

        @Test
        @DisplayName("Key follows the contract schema environment:module:resource:identifier")
        void testKeyFormat() {
            stubCounter(1L);
            ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("user-101"));

            verify(redisService).executeLua(eq(LuaScriptLoader.INCREMENT_SCRIPT), eq(Long.class),
                    keys.capture(), anyList());
            assertThat(keys.getValue()).hasSize(1);
            // Window start truncates 12:00:30 down to 12:00:00 -> epoch second 1789041600.
            assertThat(keys.getValue().get(0))
                    .isEqualTo("dev:ratelimiter:fixed:user-101:" + BASE_TIME.minusSeconds(30).getEpochSecond());
        }

        @Test
        @DisplayName("Requests inside one window share a key; the next window gets its own")
        void testKeyChangesOnlyAtWindowBoundary() {
            stubCounter(1L);
            ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("user-1"));

            RedisFixedWindowRateLimiter later = new RedisFixedWindowRateLimiter(
                    properties, new DefaultRateLimitKeyResolver(), null,
                    Clock.fixed(BASE_TIME.plusSeconds(20), ZoneOffset.UTC),
                    redisService, new RedisKeyBuilder());
            later.allowRequest(context("user-1"));

            RedisFixedWindowRateLimiter nextWindow = new RedisFixedWindowRateLimiter(
                    properties, new DefaultRateLimitKeyResolver(), null,
                    Clock.fixed(BASE_TIME.plusSeconds(40), ZoneOffset.UTC),
                    redisService, new RedisKeyBuilder());
            nextWindow.allowRequest(context("user-1"));

            verify(redisService, org.mockito.Mockito.times(3))
                    .executeLua(eq(LuaScriptLoader.INCREMENT_SCRIPT), eq(Long.class), keys.capture(), anyList());
            List<List<String>> captured = keys.getAllValues();
            assertThat(captured.get(0)).isEqualTo(captured.get(1));
            assertThat(captured.get(2)).isNotEqualTo(captured.get(0));
        }

        @Test
        @DisplayName("Distinct clients receive distinct keys")
        void testKeysArePerClient() {
            stubCounter(1L);
            ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("client-a"));
            rateLimiter.allowRequest(context("client-b"));

            verify(redisService, org.mockito.Mockito.times(2))
                    .executeLua(eq(LuaScriptLoader.INCREMENT_SCRIPT), eq(Long.class), keys.capture(), anyList());
            assertThat(keys.getAllValues().get(0)).isNotEqualTo(keys.getAllValues().get(1));
        }
    }

    @Nested
    @DisplayName("2. Lua invocation and TTL")
    class LuaInvocation {

        @Test
        @DisplayName("Delegates to increment.lua with increment 1 and TTL = window + 10s")
        void testScriptArguments() {
            stubCounter(1L);
            ArgumentCaptor<List<String>> args = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("user-1"));

            verify(redisService).executeLua(eq(LuaScriptLoader.INCREMENT_SCRIPT), eq(Long.class),
                    anyList(), args.capture());
            assertThat(args.getValue()).containsExactly("1", "70");
        }

        @Test
        @DisplayName("TTL tracks the configured window duration")
        void testTtlTracksWindow() {
            properties.setDefaultWindow(Duration.ofSeconds(30));
            stubCounter(1L);
            ArgumentCaptor<List<String>> args = ArgumentCaptor.forClass(List.class);

            rateLimiter.allowRequest(context("user-1"));

            verify(redisService).executeLua(any(), any(), anyList(), args.capture());
            assertThat(args.getValue().get(1)).isEqualTo("40");
        }
    }

    @Nested
    @DisplayName("3. Decision boundaries")
    class DecisionBoundaries {

        @Test
        @DisplayName("Counter below capacity is allowed with the correct remaining count")
        void testAllowedBelowCapacity() {
            stubCounter(2L);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.limit()).isEqualTo(CAPACITY);
            assertThat(decision.remainingRequests()).isEqualTo(3L);
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_ALLOWED);
        }

        @Test
        @DisplayName("Counter exactly at capacity is the last allowed request, with zero remaining")
        void testAllowedAtCapacityBoundary() {
            stubCounter(CAPACITY);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isTrue();
            assertThat(decision.remainingRequests()).isZero();
        }

        @Test
        @DisplayName("Counter one past capacity is rejected")
        void testRejectedPastCapacity() {
            stubCounter(CAPACITY + 1);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            assertThat(decision.allowed()).isFalse();
            assertThat(decision.limit()).isEqualTo(CAPACITY);
            assertThat(decision.reason()).isEqualTo(RateLimitConstants.REASON_EXCEEDED);
        }

        @Test
        @DisplayName("Reset time is the window boundary and retryAfter is the remaining window")
        void testResetTimeAndRetryAfter() {
            stubCounter(CAPACITY + 1);

            RateLimitDecision decision = rateLimiter.allowRequest(context("user-1"));

            // Window 12:00:00-12:01:00, evaluated at 12:00:30 -> reset 12:01:00, retry after 30s.
            assertThat(decision.resetTime()).isEqualTo(Instant.parse("2026-09-20T12:01:00.000Z"));
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
                    .thenThrow(new RedisStorageException("EVALSHA", "dev:ratelimiter:fixed:user-1:1", "boom"));

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(RedisStorageException.class);
        }

        @Test
        @DisplayName("A missing counter value is surfaced rather than silently allowed")
        void testNullCounterRejected() {
            when(redisService.executeLua(any(), any(), anyList(), anyList())).thenReturn(null);

            assertThatThrownBy(() -> rateLimiter.allowRequest(context("user-1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no counter value");
        }

        @Test
        @DisplayName("Construction requires the Redis collaborators")
        void testConstructorValidation() {
            assertThatThrownBy(() -> new RedisFixedWindowRateLimiter(
                    properties, null, null, clock, null, new RedisKeyBuilder()))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RedisFixedWindowRateLimiter(
                    properties, null, null, clock, redisService, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
