package com.gauransh.gateway.ratelimiter.config;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.FixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.RedisFixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.LeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.RedisLeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.RedisSlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.SlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.RedisSlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.SlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.RedisTokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.TokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.filter.RateLimitFilter;
import com.gauransh.gateway.ratelimiter.noop.NoOpRateLimiter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spring wiring tests for rate limiter algorithm selection.
 *
 * <p>These exist because of a defect that reached a deployed cluster: the five in-memory strategies
 * were registered as {@code @Component} beans and {@link NoOpRateLimiter} carried {@code @Primary},
 * so {@link RateLimitFilter} was injected the no-op limiter and the packaged Gateway silently
 * enforced nothing. The whole suite stayed green throughout, because every rate limiter test either
 * constructed implementations directly or mocked the filter's collaborators — none asserted what
 * Spring actually wires together.</p>
 *
 * <p>ADR-0007 makes a factory responsible for algorithm selection
 * ({@code Gateway -> RateLimiter -> RateLimiterFactory -> Correct Strategy}), and the Engineering
 * Contracts state the strategies "remain internal implementation details". These tests assert that
 * chain as observable behaviour rather than asserting that the context merely starts.</p>
 *
 * <p>Each nested class boots its own context with a different configuration, which is the only way
 * to prove selection: a single context could only ever show one outcome.</p>
 */
@DisplayName("Rate limiter Spring wiring — factory-owned algorithm selection")
class RateLimiterWiringIntegrationTest {

    /** Reads the limiter the filter was actually constructed with, rather than re-resolving it. */
    private static RateLimiter limiterInsideFilter(RateLimitFilter filter) {
        Object injected = ReflectionTestUtils.getField(filter, "rateLimiter");
        assertThat(injected).as("the filter must have been given a RateLimiter").isNotNull();
        return (RateLimiter) injected;
    }

    /**
     * Asserts the property that failed in production: one RateLimiter bean, and the filter holds
     * that exact instance. With six competing beans this fails; with a @Primary no-op it fails.
     */
    private static void assertSingleBeanIsInFilter(ApplicationContext context, Class<?> expectedType) {
        Map<String, RateLimiter> beans = context.getBeansOfType(RateLimiter.class);

        assertThat(beans)
                .as("exactly one RateLimiter bean must exist — the factory's; strategies are "
                        + "internal implementation details and must not be beans. Found: %s", beans.keySet())
                .hasSize(1);

        RateLimiter theBean = beans.values().iterator().next();
        RateLimiter inFilter = limiterInsideFilter(context.getBean(RateLimitFilter.class));

        assertThat(inFilter)
                .as("the filter must receive the factory-selected bean, not some other instance")
                .isSameAs(theBean);
        assertThat(inFilter)
                .as("the factory must have selected %s for this configuration", expectedType.getSimpleName())
                .isInstanceOf(expectedType);
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @DisplayName("1. Default configuration")
    class DefaultConfiguration {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("Default NO_OP selects NoOpRateLimiter — and it is the only RateLimiter bean")
        void testDefaultSelectsNoOp() {
            // The default must still permit everything: an unconfigured Gateway does not throttle.
            // What must NOT happen is NoOp winning when a real algorithm is configured.
            assertSingleBeanIsInFilter(context, NoOpRateLimiter.class);
        }

        @Test
        @DisplayName("No strategy implementation is registered as a bean")
        void testStrategiesAreNotBeans() {
            // The regression guard at its root: if any of these reappears as a @Component, bean
            // resolution starts competing with the factory again.
            for (Class<?> strategy : new Class<?>[]{
                    FixedWindowRateLimiter.class, SlidingWindowCounterRateLimiter.class,
                    SlidingWindowLogRateLimiter.class, TokenBucketRateLimiter.class,
                    LeakyBucketRateLimiter.class}) {
                assertThat(context.getBeansOfType(strategy))
                        .as("%s must not be a Spring bean", strategy.getSimpleName())
                        .isEmpty();
            }
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @TestPropertySource(properties = {
            "gateway.rate-limit.algorithm=FIXED_WINDOW",
            "gateway.rate-limit.redis.enabled=false"
    })
    @DisplayName("2. In-memory selection")
    class InMemorySelection {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("FIXED_WINDOW with Redis disabled selects the in-memory implementation")
        void testSelectsInMemoryFixedWindow() {
            assertSingleBeanIsInFilter(context, FixedWindowRateLimiter.class);
        }

        @Test
        @DisplayName("The configured algorithm is honoured, so NoOp is not the active limiter")
        void testNoOpIsNotActive() {
            // Stated separately and bluntly: this is the exact assertion the defect would fail.
            assertThat(limiterInsideFilter(context.getBean(RateLimitFilter.class)))
                    .isNotInstanceOf(NoOpRateLimiter.class);
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @TestPropertySource(properties = {
            "gateway.rate-limit.algorithm=FIXED_WINDOW",
            "gateway.rate-limit.redis.enabled=true"
    })
    @DisplayName("3. Redis-backed Fixed Window")
    class RedisFixedWindowSelection {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("FIXED_WINDOW with Redis enabled selects the Redis-backed implementation")
        void testSelectsRedisFixedWindow() {
            // The Redis limiters are not components and have no other construction path, so an
            // instance of this type is itself proof that the factory executed.
            assertSingleBeanIsInFilter(context, RedisFixedWindowRateLimiter.class);
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @TestPropertySource(properties = {
            "gateway.rate-limit.algorithm=SLIDING_WINDOW_COUNTER",
            "gateway.rate-limit.redis.enabled=true"
    })
    @DisplayName("4. Redis-backed Sliding Window Counter")
    class RedisSlidingCounterSelection {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("SLIDING_WINDOW_COUNTER with Redis enabled selects the Redis implementation")
        void testSelectsRedisSlidingCounter() {
            assertSingleBeanIsInFilter(context, RedisSlidingWindowCounterRateLimiter.class);
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @TestPropertySource(properties = {
            "gateway.rate-limit.algorithm=SLIDING_WINDOW_LOG",
            "gateway.rate-limit.redis.enabled=true"
    })
    @DisplayName("5. Redis-backed Sliding Window Log")
    class RedisSlidingLogSelection {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("SLIDING_WINDOW_LOG with Redis enabled selects the Redis implementation")
        void testSelectsRedisSlidingLog() {
            assertSingleBeanIsInFilter(context, RedisSlidingWindowLogRateLimiter.class);
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @TestPropertySource(properties = {
            "gateway.rate-limit.algorithm=TOKEN_BUCKET",
            "gateway.rate-limit.redis.enabled=true"
    })
    @DisplayName("6. Redis-backed Token Bucket")
    class RedisTokenBucketSelection {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("TOKEN_BUCKET with Redis enabled selects the Redis implementation")
        void testSelectsRedisTokenBucket() {
            assertSingleBeanIsInFilter(context, RedisTokenBucketRateLimiter.class);
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @TestPropertySource(properties = {
            "gateway.rate-limit.algorithm=LEAKY_BUCKET",
            "gateway.rate-limit.redis.enabled=true"
    })
    @DisplayName("7. Redis-backed Leaky Bucket")
    class RedisLeakyBucketSelection {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("LEAKY_BUCKET with Redis enabled selects the Redis implementation")
        void testSelectsRedisLeakyBucket() {
            assertSingleBeanIsInFilter(context, RedisLeakyBucketRateLimiter.class);
        }
    }

    @Nested
    @SpringBootTest(classes = GatewayApplication.class)
    @TestPropertySource(properties = {
            "gateway.rate-limit.algorithm=LEAKY_BUCKET",
            "gateway.rate-limit.redis.enabled=false"
    })
    @DisplayName("8. In-memory Leaky Bucket")
    class InMemoryLeakyBucketSelection {

        @Autowired
        private ApplicationContext context;

        @Test
        @DisplayName("Redis can be disabled per algorithm without changing the algorithm")
        void testSelectsInMemoryLeakyBucket() {
            // Proves the two configuration axes stay independent: the algorithm chooses behaviour,
            // redis.enabled chooses where the state lives.
            assertSingleBeanIsInFilter(context, LeakyBucketRateLimiter.class);
        }
    }
}
