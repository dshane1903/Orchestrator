package com.shaneduncan.orchestrator.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSystemInformation() throws Exception {
        mockMvc.perform(get("/api/system"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("orchestrator"))
            .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
            .andExpect(jsonPath("$.serverTime", notNullValue()));
    }
}

