package com.gauransh.gateway.observability.health;

import com.gauransh.gateway.observability.model.HealthResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * Health indicator evaluating local gateway instance, Redis connection, disk, and memory health.
 *
 * <p>Peer-to-peer cluster aggregation is intentionally omitted per Sprint 18 scope boundaries.</p>
 */
@Component
public class SystemHealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SystemHealthIndicator.class);

    private static final long MIN_USABLE_DISK_BYTES = 50 * 1024 * 1024L; // 50 MB
    private static final long MIN_USABLE_MEMORY_BYTES = 10 * 1024 * 1024L; // 10 MB

    private final RedisConnectionFactory redisConnectionFactory;

    public SystemHealthIndicator(@Autowired(required = false) RedisConnectionFactory redisConnectionFactory) {
        this.redisConnectionFactory = redisConnectionFactory;
    }

    /**
     * Assesses subsystem health and returns a consolidated {@link HealthResponse}.
     */
    public HealthResponse checkHealth() {
        String gateway = "UP";
        String redis = checkRedis();
        String disk = checkDisk();
        String memory = checkMemory();

        String status;
        if ("UP".equals(gateway) && "UP".equals(redis) && "UP".equals(disk) && "UP".equals(memory)) {
            status = "UP";
        } else if ("DOWN".equals(redis)) {
            // Redis failure degrades or takes down the gateway depending on policy
            status = "DOWN";
        } else {
            status = "DEGRADED";
        }

        return new HealthResponse(status, gateway, redis, disk, memory);
    }

    private String checkRedis() {
        if (redisConnectionFactory == null) {
            return "DOWN";
        }
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String ping = connection.ping();
            return "PONG".equalsIgnoreCase(ping) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.debug("Redis health probe encountered failure: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkDisk() {
        try {
            File root = new File(".");
            return root.getUsableSpace() >= MIN_USABLE_DISK_BYTES ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkMemory() {
        try {
            Runtime runtime = Runtime.getRuntime();
            long free = runtime.freeMemory() + (runtime.maxMemory() - runtime.totalMemory());
            return free >= MIN_USABLE_MEMORY_BYTES ? "UP" : "DOWN";
        } catch (Exception e) {
            return "DOWN";
        }
    }
}
