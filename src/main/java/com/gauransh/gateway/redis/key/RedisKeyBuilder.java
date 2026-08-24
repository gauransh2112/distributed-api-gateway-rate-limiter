package com.gauransh.gateway.redis.key;

import java.util.Objects;

/**
 * Standardized Redis key builder enforcing the repository's authoritative key schema:
 * {@code <environment>:<module>:<resource>:<identifier>} (e.g., {@code dev:ratelimiter:user:123} or {@code prod:ratelimiter:user:123}).
 *
 * <p>Aligned with Engineering Contracts (Section: Key Naming Strategy) & ADR-0008 Key Design Rules.
 * Guarantees key determinism, environment-awareness, lowercase formatting, separator consistency, and collision prevention.</p>
 */
public class RedisKeyBuilder {

    public static final String DEFAULT_ENVIRONMENT = "dev";
    private static final String SEPARATOR = ":";

    private final String defaultEnvironment;

    public RedisKeyBuilder() {
        this(DEFAULT_ENVIRONMENT);
    }

    public RedisKeyBuilder(String defaultEnvironment) {
        if (defaultEnvironment == null || defaultEnvironment.isBlank()) {
            throw new IllegalArgumentException("Default environment prefix must not be null or blank");
        }
        this.defaultEnvironment = defaultEnvironment.trim().toLowerCase();
    }

    /**
     * Builds a Redis key using the default environment ("dev").
     *
     * @param module     the logical module name (e.g., "ratelimiter", "metrics")
     * @param resource   the target resource type (e.g., "user", "ip", "tokenbucket")
     * @param identifier the unique identifier (e.g., "client-101", "192.168.1.1")
     * @return formatted key string in lowercase (e.g., "dev:ratelimiter:user:123")
     */
    public String buildKey(String module, String resource, String identifier) {
        return buildKey(this.defaultEnvironment, module, resource, identifier);
    }

    /**
     * Builds a Redis key using an explicit environment prefix.
     *
     * @param environment the environment prefix (e.g., "dev", "prod", "test")
     * @param module      the logical module name
     * @param resource    the target resource type
     * @param identifier  the unique identifier
     * @return formatted key string in lowercase (e.g., "prod:ratelimiter:user:123")
     */
    public String buildKey(String environment, String module, String resource, String identifier) {
        validatePart("environment", environment);
        validatePart("module", module);
        validatePart("resource", resource);
        validatePart("identifier", identifier);

        return String.join(SEPARATOR,
                environment.trim().toLowerCase(),
                module.trim().toLowerCase(),
                resource.trim().toLowerCase(),
                identifier.trim().toLowerCase());
    }

    public String getDefaultEnvironment() {
        return defaultEnvironment;
    }

    private void validatePart(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(String.format("Key component [%s] must not be null or blank", name));
        }
    }
}
