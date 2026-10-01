package com.gauransh.gateway.ratelimiter.filter;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import com.gauransh.gateway.ratelimiter.constant.RateLimitConstants;
import com.gauransh.gateway.ratelimiter.exception.RateLimitBackendUnavailableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Behaviour of health endpoints and rate-limited endpoints when Redis is unavailable.
 *
 * <p>Redis is pointed at a port nothing listens on, so every Redis operation genuinely fails. The
 * application still starts: {@code LuaScriptLoader} deliberately tolerates startup registration
 * failures because "Redis unavailability must never prevent startup".</p>
 *
 * <p>This is the test for the contradiction that ADR-0016 v1.1 resolves. ADR-0008 requires that on
 * Redis unavailability the Gateway <em>reports</em> degraded health. Previously the rate limiter
 * refused health requests under {@code FAIL_CLOSED} before the health subsystem ran, so the
 * endpoint whose job is to describe the outage was unreachable during it.</p>
 *
 * <p>Two different behaviours must now coexist:</p>
 *
 * <pre>
 *   rate-limited request  -> FAIL_CLOSED -> 503 RATE_LIMIT_BACKEND_UNAVAILABLE
 *   health endpoint       -> health subsystem runs -> dependency state reported
 * </pre>
 */
@SpringBootTest(classes = GatewayApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // Nothing listens here: Redis is genuinely unreachable for the whole context.
        "spring.data.redis.port=6399",
        "spring.data.redis.timeout=500ms",
        "spring.data.redis.connect-timeout=500ms",
        "gateway.rate-limit.enabled=true",
        "gateway.rate-limit.algorithm=FIXED_WINDOW",
        "gateway.rate-limit.redis.enabled=true",
        "gateway.rate-limit.default-capacity=5",
        // The default, stated explicitly so the test documents what it is exercising.
        "gateway.rate-limit.failure-policy=FAIL_CLOSED",
        "management.endpoint.health.show-details=always"
})
@DisplayName("Health and rate limiting with Redis unavailable")
class HealthUnderRedisFailureIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private MvcResult call(String path) throws Exception {
        return mockMvc.perform(get(path)
                .header(RateLimitConstants.HEADER_CLIENT_ID, "redisdown-" + UUID.randomUUID())).andReturn();
    }

    @Test
    @DisplayName("A rate-limited request still fails closed with 503 and the documented error code")
    void testRateLimitedRequestStillFailsClosed() throws Exception {
        MvcResult result = call("/api/v1/gateway/anything");

        assertThat(result.getResponse().getStatus())
                .as("FAIL_CLOSED behaviour for forwarded-path requests is unchanged")
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(result.getResponse().getContentAsString())
                .contains(RateLimitBackendUnavailableException.ERROR_CODE);
    }

    @Test
    @DisplayName("/health reaches its controller instead of being refused by the rate limiter")
    void testHealthEndpointExecutes() throws Exception {
        MvcResult result = call("/health");

        assertThat(result.getResponse().getStatus())
                .as("/health must be answered by the health subsystem, not refused ahead of it")
                .isEqualTo(HttpStatus.OK.value());
        assertThat(result.getResponse().getContentAsString())
                .as("the response must come from the health endpoint, not the failure policy")
                .doesNotContain(RateLimitBackendUnavailableException.ERROR_CODE)
                .contains("\"status\":\"UP\"");
    }

    @Test
    @DisplayName("/ready reaches its controller as well")
    void testReadyEndpointExecutes() throws Exception {
        MvcResult result = call("/ready");

        assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(RateLimitBackendUnavailableException.ERROR_CODE);
    }

    @Test
    @DisplayName("/actuator/health reports the Redis dependency as DOWN, which is the point")
    void testActuatorHealthReportsRedisDown() throws Exception {
        MvcResult result = call("/actuator/health");
        String body = result.getResponse().getContentAsString();

        // The request must reach the health subsystem. What it then says is the health contract's
        // business, and with Redis unreachable it must say so.
        assertThat(body)
                .as("the health document must not be replaced by the failure policy's refusal")
                .doesNotContain(RateLimitBackendUnavailableException.ERROR_CODE);
        assertThat(body)
                .as("ADR-0015 states health includes Redis; with Redis unreachable it must report DOWN")
                .contains("\"redis\"")
                .contains("DOWN");

        // Actuator's own convention: a DOWN aggregate is served as 503. That status now originates
        // from the health subsystem's assessment rather than from the rate limiter refusing entry.
        assertThat(result.getResponse().getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    @Test
    @DisplayName("Health endpoints stay answerable under sustained probing while Redis is down")
    void testHealthRemainsAnswerableRepeatedly() throws Exception {
        // A container health check probes continuously during an outage. Every probe must be
        // answered by the health subsystem; none may be refused by the rate limiter.
        for (int i = 0; i < 12; i++) {
            assertThat(call("/health").getResponse().getStatus())
                    .as("health probe %d during a Redis outage", i + 1)
                    .isEqualTo(HttpStatus.OK.value());
            assertThat(call("/actuator/health").getResponse().getContentAsString())
                    .doesNotContain(RateLimitBackendUnavailableException.ERROR_CODE);
        }
    }
}
