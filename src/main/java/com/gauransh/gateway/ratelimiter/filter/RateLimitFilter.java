package com.gauransh.gateway.ratelimiter.filter;

import com.gauransh.gateway.ratelimiter.RateLimiter;
import com.gauransh.gateway.ratelimiter.config.RateLimiterProperties;
import com.gauransh.gateway.ratelimiter.exception.RateLimitExceededException;
import com.gauransh.gateway.ratelimiter.factory.RateLimitContextFactory;
import com.gauransh.gateway.ratelimiter.metrics.RateLimitMetricsPublisher;
import com.gauransh.gateway.ratelimiter.model.RateLimitContext;
import com.gauransh.gateway.ratelimiter.model.RateLimitDecision;
import com.gauransh.gateway.ratelimiter.writer.RateLimitHeaderWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Gateway request pipeline filter enforcing rate limiting.
 *
 * <p>Delegates context construction to {@link RateLimitContextFactory}, quota evaluation
 * to {@link RateLimiter}, response header writing to {@link RateLimitHeaderWriter},
 * and telemetry to {@link RateLimitMetricsPublisher}.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final RateLimiterProperties properties;
    private final RateLimitContextFactory contextFactory;
    private final RateLimitHeaderWriter headerWriter;
    private final RateLimitMetricsPublisher metricsPublisher;
    private final HandlerExceptionResolver handlerExceptionResolver;

    public RateLimitFilter(
            @Autowired(required = false) RateLimiter rateLimiter,
            @Autowired(required = false) RateLimiterProperties properties,
            @Autowired(required = false) RateLimitContextFactory contextFactory,
            @Autowired(required = false) RateLimitHeaderWriter headerWriter,
            @Autowired(required = false) RateLimitMetricsPublisher metricsPublisher,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver handlerExceptionResolver
    ) {
        this.rateLimiter = rateLimiter;
        this.properties = properties != null ? properties : new RateLimiterProperties();
        this.contextFactory = contextFactory;
        this.headerWriter = headerWriter;
        this.metricsPublisher = metricsPublisher;
        this.handlerExceptionResolver = handlerExceptionResolver;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (!properties.isEnabled() || rateLimiter == null || contextFactory == null) {
            filterChain.doFilter(request, response);
            return;
        }

        long startNs = System.nanoTime();
        RateLimitContext context = contextFactory.createContext(request);
        RateLimitDecision decision = rateLimiter.allowRequest(context);
        long durationNs = System.nanoTime() - startNs;

        if (metricsPublisher != null) {
            metricsPublisher.publishMetrics(context, decision, Duration.ofNanos(durationNs));
        }

        if (headerWriter != null) {
            headerWriter.writeHeaders(response, decision);
        }

        if (!decision.allowed()) {
            RateLimitExceededException ex = new RateLimitExceededException(
                    "Rate limit exceeded. " + decision.reason(),
                    decision.retryAfter()
            );
            if (handlerExceptionResolver != null) {
                handlerExceptionResolver.resolveException(request, response, null, ex);
            } else {
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), ex.getMessage());
            }
            return;
        }

        filterChain.doFilter(request, response);
    }
}
