package com.shaneduncan.orchestrator.api;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shaneduncan.orchestrator.config.ApplicationInfoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemController.class)
@EnableConfigurationProperties(ApplicationInfoProperties.class)
@TestPropertySource(properties = {
    "forgeflow.application.name=forgeflow",
    "forgeflow.application.version=0.1.0-SNAPSHOT"
})
class SystemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsSystemInformation() throws Exception {
        mockMvc.perform(get("/api/system"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("forgeflow"))
            .andExpect(jsonPath("$.version").value("0.1.0-SNAPSHOT"))
            .andExpect(jsonPath("$.serverTime", notNullValue()));
    }
}
