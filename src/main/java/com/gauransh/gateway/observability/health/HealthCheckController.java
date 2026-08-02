package com.gauransh.gateway.observability.health;

import com.gauransh.gateway.shared.model.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Controller exposing lightweight system health and readiness probes for load balancers
 * and orchestration systems (e.g. Kubernetes).
 */
@RestController
@RequestMapping
public class HealthCheckController {

    public record HealthStatus(
            String status,
            String version,
            long uptimeMs
    ) {}

    private final long startTimeMs = System.currentTimeMillis();

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthStatus>> getHealth() {
        long uptime = System.currentTimeMillis() - startTimeMs;
        HealthStatus healthStatus = new HealthStatus("UP", "1.0.0-SNAPSHOT", uptime);
        return ResponseEntity.ok(ApiResponse.success(healthStatus, UUID.randomUUID().toString()));
    }

    @GetMapping("/ready")
    public ResponseEntity<ApiResponse<HealthStatus>> getReadiness() {
        long uptime = System.currentTimeMillis() - startTimeMs;
        HealthStatus readinessStatus = new HealthStatus("READY", "1.0.0-SNAPSHOT", uptime);
        return ResponseEntity.ok(ApiResponse.success(readinessStatus, UUID.randomUUID().toString()));
    }
}
