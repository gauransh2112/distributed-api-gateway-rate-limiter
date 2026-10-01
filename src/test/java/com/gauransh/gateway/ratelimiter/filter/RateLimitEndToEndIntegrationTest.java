package com.gauransh.gateway.ratelimiter.filter;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.algorithm.fixedwindow.RedisFixedWindowRateLimiter;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * End-to-end rate limiting through the real HTTP pipeline.
 *
 * <p>This is the test whose absence allowed a deployed Gateway to enforce nothing. Every other rate
 * limiter test stops short of the full path: unit tests construct limiters directly, and the filter
 * tests mock the limiter. None of them exercised</p>
 *
 * <pre>
 *   HTTP request -> RateLimitFilter -> factory-selected RateLimiter -> Redis -> 429
 * </pre>
 *
 * <p>as a single chain, so a filter wired to {@code NoOpRateLimiter} looked identical to a working
 * one. These tests drive real HTTP through the real Spring context against a real Redis.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "gateway.rate-limit.enabled=true",
        "gateway.rate-limit.algorithm=FIXED_WINDOW",
        "gateway.rate-limit.redis.enabled=true",
        "gateway.rate-limit.default-capacity=5"
})
@EnabledIf("isRedisAvailable")
@DisplayName("Rate limiting end-to-end over HTTP — live Redis")
class RateLimitEndToEndIntegrationTest {

    private static final long CAPACITY = 5L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @Autowired
    private StringRedisTemplate redisTemplate;

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
        clientId = "e2e-" + UUID.randomUUID();
    }

    /**
     * A forwarded-path URL, deliberately not an operational endpoint.
     *
     * <p>Health and readiness endpoints are designated {@code Rate Limited: No} (API Specification,
     * ADR-0016 Endpoint Scope), so they cannot be used to exercise rate limiting. No handler is
     * mapped here, so the response is 404 — but the request still travels the rate-limited path,
     * which is what these tests assert.</p>
     */
    private static final String RATE_LIMITED_PATH = "/api/v1/gateway/anything";

    private MvcResult call() throws Exception {
        return mockMvc.perform(get(RATE_LIMITED_PATH).header(RateLimitConstants.HEADER_CLIENT_ID, clientId))
                .andReturn();
    }

    @Test
    @DisplayName("The configured Redis-backed limiter is the one actually serving requests")
    void testConfiguredLimiterIsActive() {
        assertThat(rateLimiter)
                .as("the factory must have selected the Redis-backed Fixed Window limiter")
                .isInstanceOf(RedisFixedWindowRateLimiter.class);
    }

    @Test
    @DisplayName("Capacity is enforced over HTTP: the first 5 pass, the 6th is rejected with 429")
    void testCapacityEnforcedOverHttp() throws Exception {
        for (int i = 1; i <= CAPACITY; i++) {
            assertThat(call().getResponse().getStatus())
                    .as("request %d of %d must be admitted by the limiter (404 = no handler, "
                            + "but the limiter let it through)", i, CAPACITY)
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }

        assertThat(call().getResponse().getStatus())
                .as("the request past capacity must be rejected — this is what the deployed "
                        + "Gateway failed to do while NoOpRateLimiter was wired in")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }

    @Test
    @DisplayName("Documented X-RateLimit-* headers are present on an admitted request")
    void testRateLimitHeadersPresent() throws Exception {
        MvcResult result = call();

        Set<String> headers = Set.copyOf(result.getResponse().getHeaderNames());
        assertThat(headers)
                .as("the header contract must be honoured on the real response")
                .contains(RateLimitConstants.HEADER_LIMIT,
                        RateLimitConstants.HEADER_REMAINING,
                        RateLimitConstants.HEADER_RESET);

        assertThat(result.getResponse().getHeader(RateLimitConstants.HEADER_LIMIT))
                .isEqualTo(String.valueOf(CAPACITY));
        assertThat(Long.parseLong(result.getResponse().getHeader(RateLimitConstants.HEADER_REMAINING)))
                .as("the first request of the window leaves capacity - 1")
                .isEqualTo(CAPACITY - 1);
    }

    @Test
    @DisplayName("Remaining decreases with each request, proving real state rather than a no-op")
    void testRemainingDecreases() throws Exception {
        long first = Long.parseLong(call().getResponse().getHeader(RateLimitConstants.HEADER_REMAINING));
        long second = Long.parseLong(call().getResponse().getHeader(RateLimitConstants.HEADER_REMAINING));

        assertThat(second)
                .as("a no-op limiter would report an unchanging value")
                .isEqualTo(first - 1);
    }

    @Test
    @DisplayName("A rejected request carries Retry-After")
    void testRejectionCarriesRetryAfter() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            call();
        }

        MvcResult rejected = call();

        assertThat(rejected.getResponse().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(rejected.getResponse().getHeader(RateLimitConstants.HEADER_RETRY_AFTER))
                .as("clients must be told when to retry")
                .isNotNull();
    }

    @Test
    @DisplayName("Quota is consumed in Redis, not in local heap state")
    void testQuotaLivesInRedis() throws Exception {
        call();
        call();

        Set<String> keys = redisTemplate.keys("*" + clientId.toLowerCase() + "*");
        assertThat(keys)
                .as("the counter must exist in Redis — that is what makes the quota shared across "
                        + "Gateway instances rather than per-instance")
                .isNotNull()
                .isNotEmpty();

        String key = keys.iterator().next();
        assertThat(key)
                .as("the key must follow the existing contract schema; no new structure is introduced")
                .startsWith("dev:ratelimiter:fixed:");
        assertThat(redisTemplate.opsForValue().get(key))
                .as("two admitted requests must have incremented the shared counter twice")
                .isEqualTo("2");
    }

    @Test
    @DisplayName("Quota is per client, so one client's traffic does not throttle another's")
    void testQuotaIsPerClient() throws Exception {
        for (int i = 0; i < CAPACITY; i++) {
            call();
        }
        assertThat(call().getResponse().getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        clientId = "e2e-other-" + UUID.randomUUID();
        assertThat(call().getResponse().getStatus())
                .as("a different client starts with its own quota")
                .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    }
}
