package com.gauransh.gateway.ratelimiter.filter;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
 * Tests the operational-endpoint rate limiting contract.
 *
 * <p>Rate limiting applies to requests travelling the forwarding path toward an upstream. Health,
 * readiness and dependency-health endpoints are terminated by the Gateway and are never forwarded,
 * so the API Specification designates them {@code Rate Limited: No} and ADR-0016's Endpoint Scope
 * places them outside the failure policy.</p>
 *
 * <p>The defect these tests prevent: with operational endpoints rate limited, a Redis outage under
 * {@code FAIL_CLOSED} made every health endpoint answer {@code RATE_LIMIT_BACKEND_UNAVAILABLE}
 * before the health subsystem ran. ADR-0008 requires the Gateway to <em>report</em> degraded health
 * when Redis is unavailable, and the endpoint that exists to report it was unreachable in exactly
 * that circumstance. Being refused because a dependency is down is not the same as reporting that
 * it is down.</p>
 */
@SpringBootTest(classes = GatewayApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "gateway.rate-limit.enabled=true",
        "gateway.rate-limit.algorithm=FIXED_WINDOW",
        "gateway.rate-limit.redis.enabled=true",
        "gateway.rate-limit.default-capacity=3"
})
@EnabledIf("isRedisAvailable")
@DisplayName("Operational endpoints are not rate limited — live Redis")
class OperationalEndpointRateLimitExemptionTest {

    /** Deliberately small, so an exemption failure shows up immediately. */
    private static final long CAPACITY = 3L;

    @Autowired
    private MockMvc mockMvc;

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
        clientId = "op-" + UUID.randomUUID();
    }

    private MvcResult call(String path) throws Exception {
        return mockMvc.perform(get(path).header(RateLimitConstants.HEADER_CLIENT_ID, clientId)).andReturn();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/health", "/ready", "/actuator/health"})
    @DisplayName("Operational endpoints survive far more requests than the configured capacity")
    void testOperationalEndpointsAreNotRateLimited(String path) throws Exception {
        // Four times the quota. Any one 429 means the exemption is not in effect.
        for (int i = 1; i <= CAPACITY * 4; i++) {
            assertThat(call(path).getResponse().getStatus())
                    .as("request %d to %s must not be rate limited", i, path)
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/health", "/ready", "/actuator/health"})
    @DisplayName("Operational endpoints carry no rate limit headers, because no decision was made")
    void testNoRateLimitHeaders(String path) throws Exception {
        Set<String> headers = Set.copyOf(call(path).getResponse().getHeaderNames());

        assertThat(headers)
                .as("%s is not rate limited, so advertising a limit or remaining count would be a lie", path)
                .doesNotContain(RateLimitConstants.HEADER_LIMIT,
                        RateLimitConstants.HEADER_REMAINING,
                        RateLimitConstants.HEADER_RESET);
    }

    @Test
    @DisplayName("Operational traffic consumes none of the client's quota")
    void testOperationalTrafficDoesNotConsumeQuota() throws Exception {
        // Hammer the operational endpoints well past the quota...
        for (int i = 0; i < CAPACITY * 3; i++) {
            call("/health");
            call("/ready");
            call("/actuator/health");
        }

        // ...then confirm no counter exists for this client at all. Health probes run continuously
        // in a cluster; if they consumed quota, an idle client would be throttled by its own
        // liveness checks.
        Set<String> keys = redisTemplate.keys("*" + clientId.toLowerCase() + "*");
        assertThat(keys)
                .as("operational requests must not create or increment any rate limit counter")
                .isNullOrEmpty();
    }

    @Test
    @DisplayName("A rate-limited path is still rate limited — the exemption is narrow")
    void testNonOperationalPathStillRateLimited() throws Exception {
        // /api/... is a forwarded-path request. It has no handler here, so it 404s, but it must
        // still pass through the limiter and consume quota — proving this is not a blanket bypass.
        for (int i = 0; i < CAPACITY; i++) {
            assertThat(call("/api/v1/gateway/anything").getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }

        assertThat(call("/api/v1/gateway/anything").getResponse().getStatus())
                .as("a forwarded-path request past capacity must still be rejected")
                .isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

        assertThat(redisTemplate.keys("*" + clientId.toLowerCase() + "*"))
                .as("forwarded-path requests must still consume shared quota")
                .isNotEmpty();
    }

    @Test
    @DisplayName("The exemption covers health groups under /actuator/health/")
    void testHealthGroupSubPathsExempt() throws Exception {
        // A sub-path must not be blocked either; blocking one would reintroduce the defect by a
        // narrower route.
        for (int i = 0; i < CAPACITY * 3; i++) {
            assertThat(call("/actuator/health/readiness").getResponse().getStatus())
                    .isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }

    @Test
    @DisplayName("/actuator/health reports the Redis dependency, per ADR-0015")
    void testActuatorHealthReportsRedisComponent() throws Exception {
        String body = call("/actuator/health").getResponse().getContentAsString();

        assertThat(body)
                .as("ADR-0015 states health includes Redis; this is the subsystem that reports it")
                .contains("\"redis\"")
                .contains("\"status\":\"UP\"");
    }
}
