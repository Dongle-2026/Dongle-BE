package com.youthletter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
@SpringBootTest(properties="crawler.cron=-")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired MockMvc mvc;
    @Test void personalizedRoutesRequireAuthentication() throws Exception {
        for (String path : new String[]{"/api/news/recommend", "/api/policies/recommend", "/api/stocks/watchlist", "/api/admin/ai/health"})
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
    @Test void ordinaryUserCannotGenerateCardsOrReprocess() throws Exception {
        mvc.perform(post("/api/card-news/1/generate").with(user("test").roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/ai/articles/1/process").with(user("test").roles("USER"))).andExpect(status().isForbidden());
    }
    @Test void invalidDateReturns400() throws Exception {
        mvc.perform(get("/api/news").param("date","2026-99-99")).andExpect(status().isBadRequest());
    }
    @Test void jobsRequireAdminAndValidatePayload() throws Exception {
        mvc.perform(get("/api/admin/ai/jobs/1")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/ai/jobs").with(user("test").roles("USER"))
                .contentType("application/json").content("{\"articleId\":1,\"kind\":\"CARD\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/ai/jobs").with(user("admin").roles("ADMIN"))
                .contentType("application/json").content("{\"articleId\":-1,\"kind\":\"CARD\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/ai/jobs/9223372036854775807").with(user("admin").roles("ADMIN")))
                .andExpect(status().isNotFound());
    }
}
