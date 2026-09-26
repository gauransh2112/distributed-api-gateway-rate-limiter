package com.gauransh.gateway.ratelimiter.filter;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.RedisFixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.leakybucket.RedisLeakyBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowcounter.RedisSlidingWindowCounterRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.slidingwindowlog.RedisSlidingWindowLogRateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.tokenbucket.RedisTokenBucketRateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimitFailurePolicy;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.factory.RateLimitContextFactory;
import com.gauransh.gateway.ratelimiter.metrics.RateLimitMetricsPublisher;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.writer.RateLimitHeaderWriter;
import com.gauransh.gateway.redis.exception.RedisException;
import com.gauransh.gateway.redis.exception.RedisStorageException;
import com.gauransh.gateway.redis.key.RedisKeyBuilder;
import com.gauransh.gateway.redis.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.net.Socket;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration tests proving the failure policy is genuinely cross-cutting (ADR-0016).
 *
 * <p>The policy lives in {@link RateLimitFilter} rather than in any algorithm, so these drive the
 * real filter with real Redis-backed limiters and make Redis fail underneath them. Two kinds of
 * failure are used deliberately:</p>
 *
 * <ul>
 *   <li>a limiter pointed at a <strong>dead port</strong>, which produces authentic Lettuce
 *       connection failures translated by the real {@code DefaultRedisService}; and</li>
 *   <li>a <strong>fault-injecting proxy</strong> over the live {@link RedisService}, which can be
 *       switched off and back on within one test so recovery is observed on one instance without
 *       stopping the shared server.</li>
 * </ul>
 *
 * <p>The algorithms themselves are untouched by this sprint; they appear here only as the five
 * things the single policy must cover identically.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@EnabledIf("isRedisAvailable")
@DisplayName("Rate Limit Failure Policy — live Redis")
class RateLimitFailurePolicyIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00.000Z");
    private static final Clock CLOCK = Clock.fixed(NOW, java.time.ZoneOffset.UTC);
    private static final long CAPACITY = 5L;

    @Autowired
    private RedisService redisService;

    @Autowired
    private RedisKeyBuilder keyBuilder;

    @Autowired
    private RateLimitContextFactory contextFactory;

    @Autowired
    private RateLimitHeaderWriter headerWriter;

    @Autowired
    private HandlerExceptionResolver handlerExceptionResolver;

    private RateLimiterProperties properties;
    private String clientId;

    static boolean isRedisAvailable() {
        try (Socket socket = new Socket("localhost", 6379)) {
            return socket.isConnected();
        } catch (IOException e) {
            return false;
        }
    }

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        properties.setDefaultCapacity(CAPACITY);
        properties.setDefaultWindow(Duration.ofMinutes(1));
        properties.setRefillRate(2.0);
        properties.getRedis().setEnabled(true);
        clientId = "fp-" + UUID.randomUUID();
    }

    // ---------------------------------------------------------------- helpers

    /** Records whether the chain was reached, standing in for the downstream application. */
    private static final class RecordingChain implements FilterChain {
        private final AtomicInteger invocations = new AtomicInteger();

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            invocations.incrementAndGet();
        }

        boolean wasInvoked() {
            return invocations.get() > 0;
        }
    }

    /** Captures the failure signal the filter publishes, without building any metrics backend. */
    private static final class RecordingMetrics implements RateLimitMetricsPublisher {
        private volatile RateLimitFailurePolicy lastPolicy;
        private volatile Throwable lastCause;
        private final AtomicInteger failures = new AtomicInteger();

        @Override
        public void publishMetrics(RateLimitContext context,
                                   com.gauransh.gateway.ratelimiter.model.RateLimitDecision decision,
                                   Duration executionDuration) {
            // Not under test here.
        }

        @Override
        public void publishFailure(RateLimitContext context, RateLimitFailurePolicy policy,
                                   Throwable cause, Duration executionDuration) {
            this.lastPolicy = policy;
            this.lastCause = cause;
            failures.incrementAndGet();
        }
    }

    /**
     * Wraps the live {@link RedisService} and fails every call while switched off, so one limiter
     * instance can see Redis disappear and return within a single test.
     */
    private static final class FaultInjectingRedisService implements RedisService {
        private final RedisService delegate;
        private final AtomicBoolean healthy = new AtomicBoolean(true);

        private FaultInjectingRedisService(RedisService delegate) {
            this.delegate = delegate;
        }

        void breakIt() {
            healthy.set(false);
        }

        void heal() {
            healthy.set(true);
        }

        private void guard(String operation) {
            if (!healthy.get()) {
                throw new RedisStorageException(operation, "<injected>", "connection refused");
            }
        }

        @Override
        public <T> T executeLua(String scriptName, Class<T> resultType, List<String> keys, List<String> args) {
            guard("EVALSHA");
            return delegate.executeLua(scriptName, resultType, keys, args);
        }

        // Remaining operations delegate; the limiters reach Redis only through executeLua.
        @Override public void set(String k, String v) { guard("SET"); delegate.set(k, v); }
        @Override public void setWithTtl(String k, String v, Duration t) { guard("SET"); delegate.setWithTtl(k, v, t); }
        @Override public Boolean setIfAbsent(String k, String v) { guard("SETNX"); return delegate.setIfAbsent(k, v); }
        @Override public Boolean setIfAbsentWithTtl(String k, String v, Duration t) { guard("SETNX"); return delegate.setIfAbsentWithTtl(k, v, t); }
        @Override public java.util.Optional<String> getAndSet(String k, String v) { guard("GETSET"); return delegate.getAndSet(k, v); }
        @Override public java.util.Optional<String> get(String k) { guard("GET"); return delegate.get(k); }
        @Override public Long increment(String k) { guard("INCR"); return delegate.increment(k); }
        @Override public Long incrementBy(String k, long a) { guard("INCRBY"); return delegate.incrementBy(k, a); }
        @Override public Long decrement(String k) { guard("DECR"); return delegate.decrement(k); }
        @Override public Long decrementBy(String k, long a) { guard("DECRBY"); return delegate.decrementBy(k, a); }
        @Override public Boolean delete(String k) { guard("DEL"); return delegate.delete(k); }
        @Override public Boolean exists(String k) { guard("EXISTS"); return delegate.exists(k); }
        @Override public Boolean expire(String k, Duration t) { guard("EXPIRE"); return delegate.expire(k, t); }
        @Override public Boolean expireAt(String k, Instant i) { guard("EXPIREAT"); return delegate.expireAt(k, i); }
        @Override public java.util.Optional<Duration> getTtl(String k) { guard("TTL"); return delegate.getTtl(k); }
        @Override public Boolean persist(String k) { guard("PERSIST"); return delegate.persist(k); }
        @Override public void hashSet(String k, String f, String v) { guard("HSET"); delegate.hashSet(k, f, v); }
        @Override public Boolean hashSetIfAbsent(String k, String f, String v) { guard("HSETNX"); return delegate.hashSetIfAbsent(k, f, v); }
        @Override public Long hashIncrement(String k, String f, long a) { guard("HINCRBY"); return delegate.hashIncrement(k, f, a); }
        @Override public java.util.Optional<String> hashGet(String k, String f) { guard("HGET"); return delegate.hashGet(k, f); }
        @Override public Boolean hashDelete(String k, String f) { guard("HDEL"); return delegate.hashDelete(k, f); }
    }

    /** Always-failing service, standing in for a Redis that cannot be reached at all. */
    private RedisService unreachableRedis() {
        FaultInjectingRedisService service = new FaultInjectingRedisService(redisService);
        service.breakIt();
        return service;
    }

    private RateLimitFilter filterFor(RateLimiter limiter, RateLimitFailurePolicy policy,
                                      RecordingMetrics metrics) {
        properties.setFailurePolicy(policy);
        return new RateLimitFilter(limiter, properties, contextFactory, headerWriter, metrics,
                handlerExceptionResolver);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/resource");
        request.addHeader(RateLimitConstants.HEADER_CLIENT_ID, clientId);
        return request;
    }

    // The five distributed algorithms, each built the way RateLimiterConfiguration builds it.
    static Stream<org.junit.jupiter.params.provider.Arguments> algorithms() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("Fixed Window",
                        (BiFunction<RateLimiterProperties, Object[], RateLimiter>) (p, d) ->
                                new RedisFixedWindowRateLimiter(p, new DefaultRateLimitKeyResolver(), null,
                                        CLOCK, (RedisService) d[0], (RedisKeyBuilder) d[1])),
                org.junit.jupiter.params.provider.Arguments.of("Sliding Window Counter",
                        (BiFunction<RateLimiterProperties, Object[], RateLimiter>) (p, d) ->
                                new RedisSlidingWindowCounterRateLimiter(p, new DefaultRateLimitKeyResolver(), null,
                                        CLOCK, (RedisService) d[0], (RedisKeyBuilder) d[1])),
                org.junit.jupiter.params.provider.Arguments.of("Sliding Window Log",
                        (BiFunction<RateLimiterProperties, Object[], RateLimiter>) (p, d) ->
                                new RedisSlidingWindowLogRateLimiter(p, new DefaultRateLimitKeyResolver(), null,
                                        CLOCK, (RedisService) d[0], (RedisKeyBuilder) d[1])),
                org.junit.jupiter.params.provider.Arguments.of("Token Bucket",
                        (BiFunction<RateLimiterProperties, Object[], RateLimiter>) (p, d) ->
                                new RedisTokenBucketRateLimiter(p, new DefaultRateLimitKeyResolver(), null,
                                        CLOCK, (RedisService) d[0], (RedisKeyBuilder) d[1])),
                org.junit.jupiter.params.provider.Arguments.of("Leaky Bucket",
                        (BiFunction<RateLimiterProperties, Object[], RateLimiter>) (p, d) ->
                                new RedisLeakyBucketRateLimiter(p, new DefaultRateLimitKeyResolver(), null,
                                        CLOCK, (RedisService) d[0], (RedisKeyBuilder) d[1])));
    }

    // ---------------------------------------------------------------- tests

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithms")
    @DisplayName("FAIL_OPEN forwards the request for every distributed algorithm")
    void testFailOpenAcrossAllAlgorithms(String name,
                                         BiFunction<RateLimiterProperties, Object[], RateLimiter> factory)
            throws Exception {
        RateLimiter limiter = factory.apply(properties, new Object[]{unreachableRedis(), keyBuilder});
        RecordingMetrics metrics = new RecordingMetrics();
        RecordingChain chain = new RecordingChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterFor(limiter, RateLimitFailurePolicy.FAIL_OPEN, metrics)
                .doFilter(request(), response, chain);

        assertThat(chain.wasInvoked()).as("%s must reach the application under FAIL_OPEN", name).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(metrics.lastPolicy).isEqualTo(RateLimitFailurePolicy.FAIL_OPEN);
        assertThat(metrics.lastCause).isInstanceOf(RedisException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("algorithms")
    @DisplayName("FAIL_CLOSED refuses with 503 for every distributed algorithm")
    void testFailClosedAcrossAllAlgorithms(String name,
                                           BiFunction<RateLimiterProperties, Object[], RateLimiter> factory)
            throws Exception {
        RateLimiter limiter = factory.apply(properties, new Object[]{unreachableRedis(), keyBuilder});
        RecordingMetrics metrics = new RecordingMetrics();
        RecordingChain chain = new RecordingChain();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterFor(limiter, RateLimitFailurePolicy.FAIL_CLOSED, metrics)
                .doFilter(request(), response, chain);

        assertThat(chain.wasInvoked())
                .as("%s must NOT reach the application under FAIL_CLOSED", name).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(metrics.lastPolicy).isEqualTo(RateLimitFailurePolicy.FAIL_CLOSED);
    }

    @Test
    @DisplayName("The 503 body exposes no Redis internals")
    void testNoRedisInternalsInResponseBody() throws Exception {
        RateLimiter limiter = new RedisTokenBucketRateLimiter(properties, new DefaultRateLimitKeyResolver(),
                null, CLOCK, unreachableRedis(), keyBuilder);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filterFor(limiter, RateLimitFailurePolicy.FAIL_CLOSED, new RecordingMetrics())
                .doFilter(request(), response, new RecordingChain());

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(body.toLowerCase())
                .as("the client must not learn that the Gateway uses Redis, or on which key")
                .doesNotContain("redis", "evalsha", "ratelimiter:", "6379", "lua");
    }

    @Test
    @DisplayName("Rate limiting resumes automatically once Redis returns, with no restart")
    void testAutomaticRecovery() throws Exception {
        FaultInjectingRedisService redis = new FaultInjectingRedisService(redisService);
        RateLimiter limiter = new RedisFixedWindowRateLimiter(properties, new DefaultRateLimitKeyResolver(),
                null, CLOCK, redis, keyBuilder);
        RecordingMetrics metrics = new RecordingMetrics();
        RateLimitFilter filter = filterFor(limiter, RateLimitFailurePolicy.FAIL_CLOSED, metrics);

        // 1. Healthy: the request is limited normally and reaches the application.
        RecordingChain healthyChain = new RecordingChain();
        filter.doFilter(request(), new MockHttpServletResponse(), healthyChain);
        assertThat(healthyChain.wasInvoked()).as("limiting works while Redis is healthy").isTrue();

        // 2. Redis disappears: the policy applies.
        redis.breakIt();
        RecordingChain brokenChain = new RecordingChain();
        MockHttpServletResponse brokenResponse = new MockHttpServletResponse();
        filter.doFilter(request(), brokenResponse, brokenChain);
        assertThat(brokenChain.wasInvoked()).isFalse();
        assertThat(brokenResponse.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());

        // 3. Redis returns. Same filter, same limiter, same JVM, no restart and no reconfiguration.
        redis.heal();
        RecordingChain recoveredChain = new RecordingChain();
        MockHttpServletResponse recoveredResponse = new MockHttpServletResponse();
        filter.doFilter(request(), recoveredResponse, recoveredChain);

        assertThat(recoveredChain.wasInvoked())
                .as("no failure latch may survive recovery: the next request must limit normally")
                .isTrue();
        assertThat(recoveredResponse.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("Quota enforcement is intact after a failure window, proving no fake local fallback")
    void testNoLocalFallbackStateDuringOutage() throws Exception {
        FaultInjectingRedisService redis = new FaultInjectingRedisService(redisService);
        RateLimiter limiter = new RedisFixedWindowRateLimiter(properties, new DefaultRateLimitKeyResolver(),
                null, CLOCK, redis, keyBuilder);
        RateLimitFilter filter = filterFor(limiter, RateLimitFailurePolicy.FAIL_OPEN, new RecordingMetrics());

        // Spend two of the five permitted requests while Redis is healthy.
        for (int i = 0; i < 2; i++) {
            filter.doFilter(request(), new MockHttpServletResponse(), new RecordingChain());
        }

        // Ten requests pass through unlimited during the outage. None may be counted anywhere.
        redis.breakIt();
        for (int i = 0; i < 10; i++) {
            RecordingChain chain = new RecordingChain();
            filter.doFilter(request(), new MockHttpServletResponse(), chain);
            assertThat(chain.wasInvoked()).isTrue();
        }
        redis.heal();

        // Exactly three of the original five remain: the outage neither consumed quota nor
        // accumulated a shadow count to reconcile.
        int allowedAfterRecovery = 0;
        for (int i = 0; i < 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            RecordingChain chain = new RecordingChain();
            filter.doFilter(request(), response, chain);
            if (chain.wasInvoked()) {
                allowedAfterRecovery++;
            }
        }

        assertThat(allowedAfterRecovery)
                .as("the pre-outage count of 2 stands: 5 - 2 = 3 requests remain")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("A genuinely unreachable Redis port produces a real RedisException, not a stub")
    void testAuthenticConnectionFailureIsHandled() throws Exception {
        // Nothing listens here; Lettuce fails to connect and DefaultRedisService translates it.
        org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory factory =
                new org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory(
                        new org.springframework.data.redis.connection.RedisStandaloneConfiguration("localhost", 6399));
        factory.afterPropertiesSet();
        try {
            org.springframework.data.redis.core.StringRedisTemplate template =
                    new org.springframework.data.redis.core.StringRedisTemplate(factory);
            template.afterPropertiesSet();

            com.gauransh.gateway.redis.script.LuaScriptLoader loader =
                    new com.gauransh.gateway.redis.script.LuaScriptLoader(template,
                            List.of(com.gauransh.gateway.redis.script.LuaScriptLoader.INCREMENT_SCRIPT));
            RedisService deadRedis = new com.gauransh.gateway.redis.service.DefaultRedisService(template,
                    new com.gauransh.gateway.redis.script.DefaultLuaExecutor(template, loader));

            RateLimiter limiter = new RedisFixedWindowRateLimiter(properties, new DefaultRateLimitKeyResolver(),
                    null, CLOCK, deadRedis, keyBuilder);
            RecordingMetrics metrics = new RecordingMetrics();
            MockHttpServletResponse response = new MockHttpServletResponse();
            RecordingChain chain = new RecordingChain();

            filterFor(limiter, RateLimitFailurePolicy.FAIL_CLOSED, metrics)
                    .doFilter(request(), response, chain);

            assertThat(response.getStatus())
                    .as("an authentic connection failure must be absorbed by the policy")
                    .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
            assertThat(chain.wasInvoked()).isFalse();
            assertThat(metrics.lastCause)
                    .as("the driver failure must arrive as the Redis module's own exception type")
                    .isInstanceOf(RedisException.class);
        } catch (AssertionError e) {
            throw e;
        } catch (Exception e) {
            fail("the dead-port failure escaped the policy as " + e.getClass().getName(), e);
        } finally {
            factory.destroy();
        }
    }

    @Test
    @DisplayName("A configuration error still propagates and is never converted into 503")
    void testConfigurationErrorPropagates() {
        properties.setDefaultCapacity(0L);   // invalid: the limiter throws before touching Redis
        RateLimiter limiter = new RedisTokenBucketRateLimiter(properties, new DefaultRateLimitKeyResolver(),
                null, CLOCK, redisService, keyBuilder);
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        filterFor(limiter, RateLimitFailurePolicy.FAIL_CLOSED, new RecordingMetrics())
                                .doFilter(request(), response, chain))
                .as("a misconfiguration is not an outage and must not be masked as one")
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(chain.wasInvoked()).isFalse();
        assertThat(response.getStatus()).isNotEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    @DisplayName("Requests are unaffected by the policy while Redis is healthy")
    void testHealthyRedisUnaffected() throws Exception {
        RateLimiter limiter = new RedisTokenBucketRateLimiter(properties, new DefaultRateLimitKeyResolver(),
                null, CLOCK, redisService, keyBuilder);
        RecordingMetrics metrics = new RecordingMetrics();
        MockHttpServletResponse response = new MockHttpServletResponse();
        RecordingChain chain = new RecordingChain();

        filterFor(limiter, RateLimitFailurePolicy.FAIL_CLOSED, metrics)
                .doFilter(request(), response, chain);

        assertThat(chain.wasInvoked()).isTrue();
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(metrics.failures.get()).as("no failure signal on a healthy path").isZero();
    }
}
