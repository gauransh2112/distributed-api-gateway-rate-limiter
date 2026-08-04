package com.gauransh.gateway.ratelimiter.filter;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.exception.RateLimitExceededException;
import com.gauransh.gateway.ratelimiter.factory.RateLimitContextFactory;
import com.gauransh.gateway.ratelimiter.metrics.NoOpRateLimitMetricsPublisher;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.resolver.DefaultRateLimitKeyResolver;
import com.gauransh.gateway.ratelimiter.writer.RateLimitHeaderWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimiter rateLimiter;
    private RateLimiterProperties properties;
    private HandlerExceptionResolver exceptionResolver;
    private FilterChain filterChain;
    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        rateLimiter = mock(RateLimiter.class);
        properties = new RateLimiterProperties();
        properties.setEnabled(true);
        exceptionResolver = mock(HandlerExceptionResolver.class);
        filterChain = mock(FilterChain.class);

        RateLimitContextFactory contextFactory = new RateLimitContextFactory(
                new DefaultRateLimitKeyResolver(),
                Clock.systemUTC()
        );
        RateLimitHeaderWriter headerWriter = new RateLimitHeaderWriter();
        NoOpRateLimitMetricsPublisher metricsPublisher = new NoOpRateLimitMetricsPublisher();

        filter = new RateLimitFilter(
                rateLimiter,
                properties,
                contextFactory,
                headerWriter,
                metricsPublisher,
                exceptionResolver
        );
    }

    @Test
    @DisplayName("Should pass request down filter chain when rate limiter permits request")
    void shouldAllowRequestWhenPermitted() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.allowRequest(any(RateLimitContext.class)))
                .thenReturn(RateLimitDecision.allowed(100, 99, Instant.now().plusSeconds(60), "ALLOWED"));

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(exceptionResolver);
        assertEquals("100", response.getHeader("X-RateLimit-Limit"));
        assertEquals("99", response.getHeader("X-RateLimit-Remaining"));
        assertNotNull(response.getHeader("X-RateLimit-Reset"));
    }

    @Test
    @DisplayName("Should invoke exception resolver when rate limiter rejects request")
    void shouldRejectRequestWhenExceeded() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(rateLimiter.allowRequest(any(RateLimitContext.class)))
                .thenReturn(RateLimitDecision.rejected(100, Instant.now().plusSeconds(10), Duration.ofSeconds(10), "RATE_LIMIT_EXCEEDED"));

        filter.doFilterInternal(request, response, filterChain);

        verifyNoInteractions(filterChain);

        ArgumentCaptor<Exception> captor = ArgumentCaptor.forClass(Exception.class);
        verify(exceptionResolver).resolveException(eq(request), eq(response), eq(null), captor.capture());

        assertInstanceOf(RateLimitExceededException.class, captor.getValue());
        RateLimitExceededException ex = (RateLimitExceededException) captor.getValue();
        assertEquals(10L, ex.getRetryAfterSeconds());
    }

    @Test
    @DisplayName("Should bypass rate limiter evaluation when disabled via properties")
    void shouldBypassWhenDisabled() throws ServletException, IOException {
        properties.setEnabled(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/orders");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiter);
        verifyNoInteractions(exceptionResolver);
    }
}
