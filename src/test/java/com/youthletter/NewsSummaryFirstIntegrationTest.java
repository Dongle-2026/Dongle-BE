package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 홈 뉴스 목록(/api/news, 피드 포함)은 AI 요약 있는 기사를 먼저, 그 안에서 최신순. 검색은 최신순 그대로. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class NewsSummaryFirstIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    String m;
    Long noSummary, summaryA, stub, summaryB, expired;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        m = UUID.randomUUID().toString().substring(0, 8);
        expired = article("11:00");   // 요약이 있었지만 만료(valid_to) → 요약 없음
        noSummary = article("10:00");
        summaryA = article("09:00");
        stub = article("08:00");      // stub 요약 → 요약 없음
        summaryB = article("07:00");
        summary(summaryA, "실제 요약 A", false);
        summary(summaryB, "실제 요약 B", false);
        summary(stub, "[STUB] 가짜 요약", false);
        summary(expired, "예전 요약", true);
    }

    Long article(String time) {
        return jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at,crawled_at) "
                        + "VALUES (?,'t',?,'본문','저축',?::timestamp,now()) RETURNING id", Long.class,
                "sf-" + UUID.randomUUID(), "요약순서" + m + " " + time, "2001-02-03 " + time);
    }

    void summary(Long articleId, String text, boolean expiredSummary) {
        jdbc.update("INSERT INTO news_summaries(article_id, summary_text, summary_version, model_version, valid_to) VALUES (?,?,1,'t',?)",
                articleId, text, expiredSummary ? java.sql.Timestamp.valueOf("2001-02-04 00:00:00") : null);
    }

    List<Long> ids(String url, String arrayPath, String idField) throws Exception {
        JsonNode root = json.readTree(mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        List<Long> out = new ArrayList<>();
        for (JsonNode n : root.at(arrayPath)) out.add(n.get(idField).asLong());
        return out;
    }

    @Test void homeListShowsSummarizedArticlesFirstThenNewest() throws Exception {
        assertThat(ids("/api/news?date=2001-02-03", "/data/content", "id"))
                .containsExactly(summaryA, summaryB, expired, noSummary, stub);
        assertThat(ids("/api/news?category=saving&date=2001-02-03&size=2", "/data/content", "id"))
                .containsExactly(summaryA, summaryB);
        assertThat(ids("/api/news?category=saving&date=2001-02-03&size=2&page=1", "/data/content", "id"))
                .containsExactly(expired, noSummary);
    }

    @Test void cardFeedAlsoShowsSummarizedArticlesFirst() throws Exception {
        assertThat(ids("/api/news?limit=5&page=1&category=saving", "/data", "id"))
                .containsExactly(summaryA, summaryB, expired, noSummary, stub);
    }

    @Test void keywordSearchKeepsNewestFirst() throws Exception {
        assertThat(ids("/api/news/search?q=" + m, "/data/content", "id"))
                .containsExactly(expired, noSummary, summaryA, stub, summaryB);
        assertThat(ids("/api/news?keyword=" + m, "/data/content", "id"))
                .containsExactly(expired, noSummary, summaryA, stub, summaryB);
    }
}
