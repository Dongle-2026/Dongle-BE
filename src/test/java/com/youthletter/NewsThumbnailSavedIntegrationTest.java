package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.youthletter.domain.news.service.ThumbnailBackfillService;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 기사 썸네일(목록·상세·읽은 기사·키워드)과 목록 저장 여부(isSaved), 기존 기사 썸네일 채우기. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class NewsThumbnailSavedIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    @Autowired ThumbnailBackfillService backfill;
    Long userId, withImage, without;
    String bearer, m;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        m = UUID.randomUUID().toString().substring(0, 8);
        userId = jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'t',?) RETURNING id", Long.class, m + "@test.invalid", "th" + m);
        bearer = "Bearer " + tokens.createAccessToken(userId, m + "@test.invalid", "USER");
        withImage = article("http://img.test/" + m + ".jpg", "u1-" + m);
        without = article(null, "u2-" + m);
    }
    Long article(String image, String url) {
        return jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at,thumbnail_url) VALUES (?,'t',?,'본문','금융',now(),?) RETURNING id",
                Long.class, url, "썸네일" + m, image);
    }
    JsonNode find(JsonNode content, Long id) {
        for (JsonNode n : content) if (n.get("id").asLong() == id) return n;
        return null;
    }

    @Test void listAndDetailExposeImageAndSavedFlag() throws Exception {
        mvc.perform(post("/api/news/" + withImage + "/save").header("Authorization", bearer)).andExpect(status().isOk());
        JsonNode mine = json.readTree(mvc.perform(get("/api/news").param("keyword", "썸네일" + m).header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/content");
        assertThat(find(mine, withImage).get("isSaved").asBoolean()).isTrue();
        assertThat(find(mine, withImage).get("image").asText()).isEqualTo("http://img.test/" + m + ".jpg");
        assertThat(find(mine, without).get("isSaved").asBoolean()).isFalse();
        assertThat(find(mine, without).has("image")).isFalse();

        JsonNode anon = json.readTree(mvc.perform(get("/api/news/search").param("q", "썸네일" + m))
                .andReturn().getResponse().getContentAsString()).at("/data/content");
        assertThat(find(anon, withImage).has("isSaved")).isFalse(); // 비로그인
        JsonNode searched = json.readTree(mvc.perform(get("/api/news/search").param("q", "썸네일" + m).header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString()).at("/data/content");
        assertThat(find(searched, withImage).get("isSaved").asBoolean()).isTrue();

        mvc.perform(get("/api/news/" + withImage)).andExpect(jsonPath("$.data.image").value("http://img.test/" + m + ".jpg"));
        mvc.perform(get("/api/me/saved-news").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.content[0].articleId").value(withImage))
                .andExpect(jsonPath("$.data.content[0].image").value("http://img.test/" + m + ".jpg"));
    }

    @Test void readHistoryAndKeywordCardsUseThumbnail() throws Exception {
        mvc.perform(post("/api/news/" + withImage + "/read").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/me/read-history").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.content[0].thumbnail").value("http://img.test/" + m + ".jpg"))
                .andExpect(jsonPath("$.data.content[0].image").value("http://img.test/" + m + ".jpg"));
        Long term = jdbc.queryForObject("INSERT INTO financial_terms(name,path,definition,simple_definition) VALUES (?,'t','d','s') RETURNING id", Long.class, "썸" + m);
        jdbc.update("INSERT INTO article_term_highlights(article_id,term_id,position_start,position_end) VALUES (?,?,0,1),(?,?,0,1)", without, term, withImage, term);
        JsonNode cards = json.readTree(mvc.perform(get("/api/keywords").param("limit", "20")).andReturn().getResponse().getContentAsString()).get("data");
        JsonNode card = null;
        for (JsonNode c : cards) if (c.get("keyword").asText().equals("썸" + m)) card = c;
        assertThat(card.get("image").asText()).isEqualTo("http://img.test/" + m + ".jpg"); // 썸네일 있는 기사에서
    }

    @Test void backfillReadsOgImageFromArticlePage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] page = "<html><head><meta property='og:image' content='/img/og.jpg'></head><body>x</body></html>".getBytes(StandardCharsets.UTF_8);
        byte[] none = "<html><head></head><body>x</body></html>".getBytes(StandardCharsets.UTF_8);
        server.createContext("/a", ex -> { ex.sendResponseHeaders(200, page.length); ex.getResponseBody().write(page); ex.close(); });
        server.createContext("/b", ex -> { ex.sendResponseHeaders(200, none.length); ex.getResponseBody().write(none); ex.close(); });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            jdbc.update("UPDATE news_articles SET thumbnail_url = coalesce(thumbnail_url, 'http://preset/x.jpg') WHERE thumbnail_url IS NULL"); // 다른 기사 제외
            Long a = article(null, base + "/a?" + m), b = article(null, base + "/b?" + m);
            var result = backfill.backfill(10);
            assertThat(result.checked()).isEqualTo(2);
            assertThat(result.updated()).isEqualTo(1);
            assertThat(result.notFound()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM news_articles WHERE id=?", String.class, a)).isEqualTo(base + "/img/og.jpg");
            assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM news_articles WHERE id=?", String.class, b)).isNull();
        } finally {
            server.stop(0);
        }
        mvc.perform(post("/api/admin/news/thumbnails").header("Authorization", bearer)).andExpect(status().isForbidden());
    }

    @Test void backfillStopsOnRateLimit() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        java.util.concurrent.atomic.AtomicInteger hits = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/limited", ex -> { hits.incrementAndGet(); ex.sendResponseHeaders(429, -1); ex.close(); });
        server.start();
        try {
            jdbc.update("UPDATE news_articles SET thumbnail_url = coalesce(thumbnail_url, 'http://preset/x.jpg') WHERE thumbnail_url IS NULL");
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            for (int i = 0; i < 3; i++) article(null, base + "/limited?" + i + m);
            var result = backfill.backfill(10);
            assertThat(result.rateLimited()).isTrue();
            assertThat(hits.get()).isEqualTo(1); // 429 이후 더 요청하지 않음
            assertThat(result.updated()).isZero();
        } finally {
            server.stop(0);
        }
    }

    @Test void clearsSiteLogoThumbnailsOnly() {
        Long logo = article("https://static.hankyung.com/img/logo/logo-news-sns.png?v=" + m, "u3-" + m);
        assertThat(backfill.clearPlaceholders()).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM news_articles WHERE id=?", String.class, logo)).isNull();
        assertThat(jdbc.queryForObject("SELECT thumbnail_url FROM news_articles WHERE id=?", String.class, withImage))
                .isEqualTo("http://img.test/" + m + ".jpg");
    }
}
