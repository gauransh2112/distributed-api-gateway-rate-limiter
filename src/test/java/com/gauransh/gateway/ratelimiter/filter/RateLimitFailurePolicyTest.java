package com.gauransh.gateway.ratelimiter.filter;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimitFailurePolicy;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.exception.RateLimitBackendUnavailableException;
import com.gauransh.gateway.ratelimiter.factory.RateLimitContextFactory;
import com.gauransh.gateway.ratelimiter.metrics.RateLimitMetricsPublisher;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.writer.RateLimitHeaderWriter;
import com.gauransh.gateway.redis.exception.LuaExecutionException;
import com.gauransh.gateway.redis.exception.LuaScriptNotLoadedException;
import com.gauransh.gateway.redis.exception.RedisException;
import com.gauransh.gateway.redis.exception.RedisStorageException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the failure policy applied by {@link RateLimitFilter} (ADR-0016).
 *
 * <p>The decisive tests here are the exception-boundary ones. The policy exists to absorb an
 * <em>unavailable</em> Redis; it must not absorb a <em>misconfigured</em> Gateway. Under FAIL_OPEN
 * a broadened catch would turn an invalid capacity into silently unlimited traffic, and the logs
 * would look identical to an infrastructure outage.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Rate Limit Failure Policy — unit")
class RateLimitFailurePolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-24T12:00:00.000Z");
    private static final String CLIENT_ID = "user-101";

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private RateLimitContextFactory contextFactory;

    @Mock
    private RateLimitHeaderWriter headerWriter;

    @Mock
    private RateLimitMetricsPublisher metricsPublisher;

    @Mock
    private HandlerExceptionResolver handlerExceptionResolver;

    @Mock
    private FilterChain filterChain;

    private RateLimiterProperties properties;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        request = new MockHttpServletRequest("GET", "/api/v1/resource");
        response = new MockHttpServletResponse();
        when(contextFactory.createContext(any())).thenReturn(context());
    }

    private RateLimitContext context() {
        return new RateLimitContext(CLIENT_ID, "/api/v1/resource", "GET", NOW, "127.0.0.1",
                Map.of(RateLimitConstants.HEADER_CLIENT_ID, List.of(CLIENT_ID)));
    }

    private RateLimitFilter filter() {
        return new RateLimitFilter(rateLimiter, properties, contextFactory, headerWriter,
                metricsPublisher, handlerExceptionResolver);
    }

    private void redisFails(RuntimeException failure) {
        when(rateLimiter.allowRequest(any())).thenThrow(failure);
    }

    @Nested
    @DisplayName("1. Default policy")
    class DefaultPolicy {

        @Test
        @DisplayName("The default is FAIL_CLOSED, so a protection boundary never disappears silently")
        void testDefaultIsFailClosed() {
            assertThat(new RateLimiterProperties().getFailurePolicy())
                    .isEqualTo(RateLimitFailurePolicy.FAIL_CLOSED);
        }

        @Test
        @DisplayName("A null policy is treated as FAIL_CLOSED rather than crashing or failing open")
        void testNullPolicyFallsBackToFailClosed() throws Exception {
            properties.setFailurePolicy(null);
            redisFails(new RedisStorageException("GET", "k", "connection refused"));

            filter().doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(any(), any());
            verify(handlerExceptionResolver).resolveException(any(), any(), any(),
                    any(RateLimitBackendUnavailableException.class));
        }
    }

    @Nested
    @DisplayName("2. FAIL_OPEN")
    class FailOpen {

        @BeforeEach
        void useFailOpen() {
            properties.setFailurePolicy(RateLimitFailurePolicy.FAIL_OPEN);
        }

        @Test
        @DisplayName("The request proceeds down the chain when Redis is unavailable")
        void testRequestProceeds() throws Exception {
            redisFails(new RedisStorageException("EVALSHA", "k", "connection refused"));

            filter().doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("No rate limit headers are written, because no decision was reached")
        void testNoHeadersWritten() throws Exception {
            redisFails(new RedisStorageException("EVALSHA", "k", "connection refused"));

            filter().doFilter(request, response, filterChain);

            verifyNoInteractions(headerWriter);
        }

        @Test
        @DisplayName("The response status is untouched, so the downstream application decides it")
        void testStatusUntouched() throws Exception {
            redisFails(new RedisStorageException("EVALSHA", "k", "connection refused"));

            filter().doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("The failure is reported to the metrics publisher with the applied policy")
        void testFailureMetricPublished() throws Exception {
            RedisException cause = new RedisStorageException("EVALSHA", "k", "connection refused");
            redisFails(cause);

            filter().doFilter(request, response, filterChain);

            ArgumentCaptor<RateLimitFailurePolicy> policyCaptor =
                    ArgumentCaptor.forClass(RateLimitFailurePolicy.class);
            verify(metricsPublisher).publishFailure(any(), policyCaptor.capture(), eq(cause), any(Duration.class));
            assertThat(policyCaptor.getValue()).isEqualTo(RateLimitFailurePolicy.FAIL_OPEN);
            verify(metricsPublisher, never()).publishMetrics(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("3. FAIL_CLOSED")
    class FailClosed {

        @BeforeEach
        void useFailClosed() {
            properties.setFailurePolicy(RateLimitFailurePolicy.FAIL_CLOSED);
        }

        @Test
        @DisplayName("The chain is never invoked, so the request never reaches the application")
        void testChainNotInvoked() throws Exception {
            redisFails(new RedisStorageException("EVALSHA", "k", "connection refused"));

            filter().doFilter(request, response, filterChain);

            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("The request is refused through the backend-unavailable exception")
        void testRefusedWithBackendUnavailable() throws Exception {
            redisFails(new RedisStorageException("EVALSHA", "k", "connection refused"));

            filter().doFilter(request, response, filterChain);

            verify(handlerExceptionResolver).resolveException(any(), any(), any(),
                    any(RateLimitBackendUnavailableException.class));
        }

        @Test
        @DisplayName("Without a resolver the filter still refuses, with 503")
        void testFallbackSendsServiceUnavailable() throws Exception {
            redisFails(new RedisStorageException("EVALSHA", "k", "connection refused"));
            RateLimitFilter filter = new RateLimitFilter(rateLimiter, properties, contextFactory,
                    headerWriter, metricsPublisher, null);

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
            verify(filterChain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("Nothing about Redis reaches the client")
        void testNoRedisInternalsExposed() throws Exception {
            redisFails(new RedisStorageException("EVALSHA",
                    "dev:ratelimiter:fixed:user-101:1790000000", "connection refused to 10.0.0.7:6379"));

            RateLimitBackendUnavailableException ex = new RateLimitBackendUnavailableException(
                    new RedisStorageException("EVALSHA", "dev:ratelimiter:fixed:user-101", "boom"));

            assertThat(ex.getMessage())
                    .doesNotContain("redis", "Redis", "EVALSHA", "dev:ratelimiter", "6379")
                    .isEqualTo("Rate limiting is temporarily unavailable. Please retry.");
        }

        @Test
        @DisplayName("No rate limit headers are written on the refusal path")
        void testNoHeadersWritten() throws Exception {
            redisFails(new RedisStorageException("EVALSHA", "k", "connection refused"));

            filter().doFilter(request, response, filterChain);

            verifyNoInteractions(headerWriter);
        }

        @Test
        @DisplayName("The failure is reported to the metrics publisher with the applied policy")
        void testFailureMetricPublished() throws Exception {
            RedisException cause = new LuaExecutionException("token_bucket.lua", "boom");
            redisFails(cause);

            filter().doFilter(request, response, filterChain);

            verify(metricsPublisher).publishFailure(any(), eq(RateLimitFailurePolicy.FAIL_CLOSED),
                    eq(cause), any(Duration.class));
        }
    }

    @Nested
    @DisplayName("4. Exception boundary — the substance of the decision")
    class ExceptionBoundary {

        static Stream<RedisException> redisFailures() {
            return Stream.of(
                    new RedisStorageException("GET", "k", "connection refused"),
                    new LuaExecutionException("increment.lua", "runtime error"),
                    new LuaScriptNotLoadedException("token_bucket.lua", "not registered"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("redisFailures")
        @DisplayName("Every Redis exception subtype is absorbed by the policy")
        void testAllRedisExceptionsAbsorbed(RedisException failure) throws Exception {
            properties.setFailurePolicy(RateLimitFailurePolicy.FAIL_OPEN);
            redisFails(failure);

            filter().doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }

        @Test
        @DisplayName("IllegalArgumentException propagates: a bad capacity is not an outage")
        void testIllegalArgumentPropagates() throws Exception {
            properties.setFailurePolicy(RateLimitFailurePolicy.FAIL_OPEN);
            redisFails(new IllegalArgumentException("Rate limit capacity must be strictly positive"));

            assertThatThrownBy(() -> filter().doFilter(request, response, filterChain))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("capacity must be strictly positive");
        }

        @Test
        @DisplayName("IllegalStateException propagates: a broken invariant is not an outage")
        void testIllegalStatePropagates() throws Exception {
            properties.setFailurePolicy(RateLimitFailurePolicy.FAIL_OPEN);
            redisFails(new IllegalStateException("Redis returned an unusable token bucket result"));

            assertThatThrownBy(() -> filter().doFilter(request, response, filterChain))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("A propagating configuration error never reaches the chain under FAIL_OPEN")
        void testPropagatingErrorDoesNotFailOpen() throws Exception {
            properties.setFailurePolicy(RateLimitFailurePolicy.FAIL_OPEN);
            redisFails(new IllegalArgumentException("Refill rate must be strictly positive and finite"));

            assertThatThrownBy(() -> filter().doFilter(request, response, filterChain))
                    .isInstanceOf(IllegalArgumentException.class);

            // The critical assertion: a misconfiguration must not be silently converted into
            // unlimited traffic the way a genuine Redis outage is.
            verify(filterChain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("5. The normal path is unaffected")
    class NormalPath {

        @Test
        @DisplayName("An allowed request still writes headers and continues")
        void testAllowedRequestUnchanged() throws Exception {
            when(rateLimiter.allowRequest(any())).thenReturn(
                    RateLimitDecision.allowed(10L, 9L, NOW.plusSeconds(60), RateLimitConstants.REASON_ALLOWED));

            filter().doFilter(request, response, filterChain);

            verify(headerWriter).writeHeaders(eq(response), any());
            verify(filterChain).doFilter(request, response);
            verify(metricsPublisher, never()).publishFailure(any(), any(), any(), any());
        }

        @Test
        @DisplayName("No Redis call is attempted when rate limiting is disabled")
        void testDisabledSkipsEvaluation() throws Exception {
            properties.setEnabled(false);

            filter().doFilter(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
            verifyNoInteractions(rateLimiter);
        }
    }
}
