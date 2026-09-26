package com.gauransh.gateway.ratelimiter.config;

/**
 * Behaviour applied when the distributed rate limiting state is unavailable at request time.
 *
 * <p>Rate limiting depends on Redis being reachable. When it is not, the Gateway cannot know how
 * much quota a client has consumed, yet it must still answer the request. This enum names the two
 * answers, per ADR-0016.</p>
 *
 * <p>Neither value is universally correct — they express different priorities, and the choice
 * belongs to whoever operates the deployment. The policy governs <em>runtime</em> failures only;
 * startup and readiness behaviour is a separate lifecycle concern that ADR-0016 deliberately leaves
 * undecided.</p>
 *
 * @see com.gauransh.gateway.ratelimiter.filter.RateLimitFilter
 */
public enum RateLimitFailurePolicy {

    /**
     * Availability-oriented: the request proceeds without rate limiting.
     *
     * <p>Rate limiting is temporarily not enforced. Appropriate where serving traffic matters more
     * than enforcing the boundary — internal services, development environments, and anywhere a
     * Redis outage must not become a Gateway outage.</p>
     */
    FAIL_OPEN,

    /**
     * Enforcement-oriented: the request is refused with 503 Service Unavailable.
     *
     * <p>Traffic is shed while the enforcement state is unavailable. This is the default, because a
     * protection boundary that silently disappears on infrastructure failure is not a protection
     * boundary: under {@link #FAIL_OPEN} a Redis outage would move the system from "rate limiting
     * enforced" to "unlimited traffic" with no configuration change and no operator decision.</p>
     */
    FAIL_CLOSED
}
