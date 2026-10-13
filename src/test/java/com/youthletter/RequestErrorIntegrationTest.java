package com.youthletter;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc
class RequestErrorIntegrationTest {
    @Autowired MockMvc mvc;
    @Test void invalidSortIs400InsteadOf500() throws Exception {
        mvc.perform(get("/api/news").param("sort","nonexistentField,desc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(4105));
    }
    @Test void unsupportedMethodIs405WithAllowHeader() throws Exception {
        mvc.perform(post("/api/news")).andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }
}
