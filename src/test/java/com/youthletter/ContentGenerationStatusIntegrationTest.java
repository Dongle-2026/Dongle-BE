package com.youthletter;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.news.service.ContentPipelineStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"crawler.cron=-", "ai-jobs.enabled=false"})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class ContentGenerationStatusIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ContentPipelineStore store;
    @Autowired JdbcTemplate jdbc;
    @Test void exposesLatestStageStatesWithoutLoginOrInternalErrors() throws Exception {
        Long id = store.save(new CrawledArticleDto("https://example.invalid/" + UUID.randomUUID(), "기사", "원문입니다.", "금융", null, null));
        jdbc.update("UPDATE ai_jobs SET status='FAILED', error_code='PRIVATE_ERROR' WHERE article_id=? AND kind='CARD'", id);
        jdbc.update("INSERT INTO ai_jobs(article_id,kind,status) VALUES (?, 'CARD', 'QUEUED')", id);
        mvc.perform(get("/api/news/" + id + "/generation"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.length()").value(3))
            .andExpect(jsonPath("$.data[1].kind").value("CARD"))
            .andExpect(jsonPath("$.data[1].status").value("QUEUED"))
            .andExpect(jsonPath("$.data[1].errorCode").doesNotExist());
        jdbc.update("DELETE FROM ai_jobs WHERE article_id=?", id);
        mvc.perform(get("/api/news/" + id + "/generation"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value("NOT_REQUESTED"));
    }
    @Test void unknownArticleDoesNotPretendToBeQueued() throws Exception {
        mvc.perform(get("/api/news/9223372036854775807/generation"))
            .andExpect(status().isNotFound());
    }
}
