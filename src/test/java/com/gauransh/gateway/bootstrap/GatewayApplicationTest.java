package com.gauransh.gateway.bootstrap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest(classes = GatewayApplication.class)
class GatewayApplicationTest {

    @Test
    @DisplayName("Application Context should load successfully without errors")
    void contextLoads() {
        assertDoesNotThrow(() -> {});
    }
}
