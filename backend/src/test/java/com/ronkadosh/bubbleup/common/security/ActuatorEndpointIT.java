package com.ronkadosh.bubbleup.common.security;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActuatorEndpointIT extends IntegrationTest {

    @Test
    void health_endpoint_is_public() throws Exception {
        mvc.perform(get("/api/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("UP")));
    }

    @Test
    void info_endpoint_is_public() throws Exception {
        mvc.perform(get("/api/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.service", is("backend")));
    }
}
