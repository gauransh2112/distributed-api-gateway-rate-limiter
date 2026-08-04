package com.gateway.ratelimiter.noop;

import com.gateway.ratelimiter.constant.RateLimitConstants;
import com.gateway.ratelimiter.model.RateLimitContext;
import com.gateway.ratelimiter.model.RateLimitDecision;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NoOpRateLimiterTest {

    private final NoOpRateLimiter rateLimiter = new NoOpRateLimiter();

    @Test
    @DisplayName("NoOpRateLimiter should always allow incoming requests without limit header constraints")
    void shouldAlwaysAllowRequests() {
        RateLimitContext context = new RateLimitContext(
                "user-123",
                "/api/v1/test",
                "GET",
                Instant.now(),
                "127.0.0.1",
                Map.of()
        );

        RateLimitDecision decision = rateLimiter.allowRequest(context);

        assertNotNull(decision);
        assertTrue(decision.allowed());
        assertNull(decision.limit());
        assertNull(decision.remainingRequests());
        assertFalse(decision.includesHeaders());
        assertEquals(RateLimitConstants.REASON_NO_OP, decision.reason());
    }
}
