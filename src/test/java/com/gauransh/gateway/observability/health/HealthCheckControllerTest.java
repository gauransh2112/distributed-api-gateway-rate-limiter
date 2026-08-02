package com.gauransh.gateway.observability.health;

import com.gauransh.gateway.bootstrap.GatewayApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthCheckController.class)
@ContextConfiguration(classes = GatewayApplication.class)
class HealthCheckControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /health should return 200 OK with success=true and status UP")
    void testGetHealth() throws Exception {
        mockMvc.perform(get("/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.version").value("1.0.0-SNAPSHOT"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("GET /ready should return 200 OK with success=true and status READY")
    void testGetReadiness() throws Exception {
        mockMvc.perform(get("/ready").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("READY"))
                .andExpect(jsonPath("$.data.version").value("1.0.0-SNAPSHOT"))
                .andExpect(jsonPath("$.requestId").exists());
    }
}
