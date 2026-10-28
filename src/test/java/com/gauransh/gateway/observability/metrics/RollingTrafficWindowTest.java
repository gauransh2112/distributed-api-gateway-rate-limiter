package com.gauransh.gateway.observability.metrics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RollingTrafficWindowTest {

    @Test
    @DisplayName("Empty window aggregates to zero counts and zero average latency")
    void emptyWindowAggregation() {
        RollingTrafficWindow window = new RollingTrafficWindow();
        RollingTrafficWindow.TrafficStats stats = window.aggregate(Duration.ofMinutes(1));

        assertThat(stats.requests()).isZero();
        assertThat(stats.successful()).isZero();
        assertThat(stats.failed()).isZero();
        assertThat(stats.averageLatencyMs()).isZero();
    }

    @Test
    @DisplayName("Records successful and failed requests and computes aggregate stats correctly")
    void recordsAndAggregatesTraffic() {
        RollingTrafficWindow window = new RollingTrafficWindow();

        window.record(true, 10.0);
        window.record(true, 20.0);
        window.record(false, 30.0);

        RollingTrafficWindow.TrafficStats stats = window.aggregate(Duration.ofMinutes(1));

        assertThat(stats.requests()).isEqualTo(3);
        assertThat(stats.successful()).isEqualTo(2);
        assertThat(stats.failed()).isEqualTo(1);
        assertThat(stats.averageLatencyMs()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("Aggregates correctly across different period durations")
    void aggregatesAcrossPeriods() {
        RollingTrafficWindow window = new RollingTrafficWindow();

        window.record(true, 15.0);

        assertThat(window.aggregate(Duration.ofMinutes(1)).requests()).isEqualTo(1);
        assertThat(window.aggregate(Duration.ofMinutes(5)).requests()).isEqualTo(1);
        assertThat(window.aggregate(Duration.ofHours(1)).requests()).isEqualTo(1);
    }
}
