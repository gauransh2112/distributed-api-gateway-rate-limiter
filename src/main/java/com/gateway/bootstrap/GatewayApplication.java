package com.gateway.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Distributed API Gateway application.
 *
 * <p>Initializes the Spring ApplicationContext and bootstraps all gateway sub-components
 * following the configured component scan path ({@code com.gateway.*}).</p>
 */
@SpringBootApplication(scanBasePackages = "com.gateway")
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
