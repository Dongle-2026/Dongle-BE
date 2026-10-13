package com.youthletter;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.news.client.dto.AiProcessResponse;
import com.youthletter.domain.news.service.NewsAiWriter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 프론트에서 "[STUB] … AI 요약입니다"가 실제 요약처럼 보이던 문제. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class StubSummaryHiddenIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired NewsAiWriter writer;
    @Autowired jakarta.persistence.EntityManager em;

    @Test void existingStubSummariesAreHiddenEverywhere() throws Exception {
        String m = UUID.randomUUID().toString().substring(0, 8);
        Long id = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?, '한국경제', ?, '본문입니다.', '경제', now()) RETURNING id",
                Long.class, "stub-" + m, "스텁" + m);
        jdbc.update("INSERT INTO news_summaries(article_id, summary_text, summary_version, model_version) VALUES (?, '[STUB] 스텁에 대한 AI 요약입니다.\n이 요약은 테스트용 stub 데이터입니다.', 1, 'stub')", id);
        mvc.perform(get("/api/news").param("keyword", "스텁" + m)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(id))
                .andExpect(jsonPath("$.data.content[0].summary").doesNotExist());
        mvc.perform(get("/api/news/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").doesNotExist())
                .andExpect(jsonPath("$.data.modelVersion").doesNotExist());
        mvc.perform(get("/api/news/" + id + "/body")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.intro").doesNotExist());
    }

    @Test void stubModeDoesNotStoreFakeSummaries() {
        String m = UUID.randomUUID().toString().substring(0, 8);
        Long id = writer.create(new CrawledArticleDto("stub-new-" + m, "제목" + m, "본문입니다.", "경제", LocalDateTime.now(), null),
                new AiProcessResponse("[STUB] 제목에 대한 AI 요약입니다.", List.of(), null), "stub");
        em.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM news_summaries WHERE article_id=?", Integer.class, id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM news_articles WHERE id=?", Integer.class, id)).isEqualTo(1); // 기사는 저장
    }
}
