package com.youthletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 노션 A12·A13·A16·A18·A28·A29 경로의 실제 동작(멱등성·소유권·인증) 검증. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class MeApiGroupAIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper json;
    @Autowired jakarta.persistence.EntityManager em; // 테스트 트랜잭션은 커밋되지 않으므로 SQL 확인 전 flush
    Long userId, otherId, termId, articleA, articleB;
    String bearer, otherBearer, access, marker;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        marker = "groupa-" + UUID.randomUUID();
        userId = user(marker);
        otherId = user(marker + "-o");
        access = tokens.createAccessToken(userId, marker + "@test.invalid", "USER");
        bearer = "Bearer " + access;
        otherBearer = "Bearer " + tokens.createAccessToken(otherId, marker + "-o@test.invalid", "USER");
        termId = jdbc.queryForObject("INSERT INTO financial_terms(name,path,definition,simple_definition) VALUES (?,'groupa','시험 정의','쉬운 정의') RETURNING id",
                Long.class, marker);
        articleA = article(marker + "-a");
        articleB = article(marker + "-b");
    }
    @AfterEach void cleanup() {
        tokens.deleteRefreshToken(userId);
        tokens.deleteRefreshToken(otherId);
        redis.delete("blacklist:" + access);
    }
    Long user(String key) {
        return jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'test',?) RETURNING id",
                Long.class, key + "@test.invalid", key.substring(key.length() - 20));
    }
    Long article(String url) {
        return jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'groupa',?,'본문','YOUTH',now()) RETURNING id",
                Long.class, url, url);
    }
    int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    // ── A12·A13·A16 용어 저장 ──
    @Test void termSaveAndUnsaveAreIdempotentAndIndependentOfLearning() throws Exception {
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/terms/" + termId + "/save").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarked").value(true));
        assertThat(count("SELECT count(*) FROM term_bookmarks WHERE user_id=? AND term_id=?", userId, termId)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM term_lookup_log WHERE user_id=?", userId)).isZero();

        mvc.perform(get("/api/me/saved-terms").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].termId").value(termId))
                .andExpect(jsonPath("$.data.content[0].name").value(marker))
                .andExpect(jsonPath("$.data.content[0].simpleDefinition").value("쉬운 정의"))
                .andExpect(jsonPath("$.data.content[0].savedAt").isNotEmpty());

        mvc.perform(post("/api/terms/" + termId + "/learn").header("Authorization", bearer)).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/terms/" + termId + "/unsave").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarked").value(false));
        assertThat(count("SELECT count(*) FROM term_bookmarks WHERE user_id=?", userId)).isZero();
        assertThat(count("SELECT count(*) FROM term_lookup_log WHERE user_id=?", userId)).isEqualTo(1); // 학습 이력 보존
        mvc.perform(get("/api/me/saved-terms").header("Authorization", bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test void savedTermsAreOwnedAndProtected() throws Exception {
        mvc.perform(post("/api/terms/" + termId + "/save").header("Authorization", bearer)).andExpect(status().isOk());
        mvc.perform(get("/api/me/saved-terms").header("Authorization", otherBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(post("/api/terms/" + termId + "/unsave").header("Authorization", otherBearer)).andExpect(status().isOk());
        assertThat(count("SELECT count(*) FROM term_bookmarks WHERE user_id=?", userId)).isEqualTo(1);

        mvc.perform(post("/api/terms/" + termId + "/save")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/terms/" + termId + "/unsave")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/saved-terms")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/terms/999999999/save").header("Authorization", bearer)).andExpect(status().isNotFound());
        mvc.perform(get("/api/terms/" + termId)).andExpect(status().isOk()); // 기존 공개 상세 유지
    }

    // ── A18 최근 읽은 기사 ──
    @Test void readHistoryGroupsByArticleNewestFirstAndPaginates() throws Exception {
        jdbc.update("INSERT INTO reading_history(user_id,article_id,read_at) VALUES (?,?,now() - interval '2 day')", userId, articleA);
        jdbc.update("INSERT INTO reading_history(user_id,article_id,read_at) VALUES (?,?,now() - interval '1 day')", userId, articleB);
        mvc.perform(post("/api/news/" + articleA + "/read").header("Authorization", bearer)).andExpect(status().isOk());
        jdbc.update("INSERT INTO reading_history(user_id,article_id,read_at) VALUES (?,?,now())", otherId, articleB);

        mvc.perform(get("/api/me/read-history").header("Authorization", bearer).param("sort", "title,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].articleId").value(articleA))
                .andExpect(jsonPath("$.data.content[0].title").value(marker + "-a"))
                .andExpect(jsonPath("$.data.content[0].sourceName").value("groupa"))
                .andExpect(jsonPath("$.data.content[0].lastReadAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[1].articleId").value(articleB));
        mvc.perform(get("/api/me/read-history").header("Authorization", bearer).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].articleId").value(articleB))
                .andExpect(jsonPath("$.data.last").value(true));
        mvc.perform(get("/api/me/read-history").header("Authorization", otherBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/me/read-history")).andExpect(status().isUnauthorized());
    }

    // ── A29 푸시 토큰 등록 ──
    @Test void deviceRegistrationUpsertsPerDevice() throws Exception {
        for (String token : new String[]{marker + "-t1", marker + "-t2"})
            mvc.perform(post("/api/me/devices").header("Authorization", bearer).contentType("application/json")
                    .content(json.writeValueAsString(Map.of("deviceId", "iphone", "token", token, "platform", "IOS"))))
                    .andExpect(status().isOk());
        em.flush();
        assertThat(count("SELECT count(*) FROM device_tokens WHERE user_id=?", userId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT token FROM device_tokens WHERE user_id=?", String.class, userId)).isEqualTo(marker + "-t2");
        mvc.perform(post("/api/me/devices").header("Authorization", bearer).contentType("application/json")
                .content("{\"deviceId\":\"iphone\",\"token\":\"x\",\"platform\":\"WEB\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/me/devices").contentType("application/json")
                .content("{\"deviceId\":\"iphone\",\"token\":\"x\",\"platform\":\"IOS\"}")).andExpect(status().isUnauthorized());
    }

    // ── A28 회원 탈퇴 ──
    @Test void deleteMeWithdrawsAndRevokesTokens() throws Exception {
        String refresh = tokens.createRefreshToken(userId);
        jdbc.update("INSERT INTO device_tokens(user_id,device_id,token,platform) VALUES (?,'iphone',?,'IOS')", userId, marker);
        mvc.perform(delete("/api/me")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/me").header("Authorization", bearer)).andExpect(status().isOk());
        em.flush();
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM users WHERE id=?", Boolean.class, userId)).isTrue();
        assertThat(count("SELECT count(*) FROM device_tokens WHERE user_id=?", userId)).isZero();
        assertThat(tokens.validateRefreshToken(userId, refresh)).isFalse();
        mvc.perform(get("/api/me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization", otherBearer)).andExpect(status().isOk());
    }
}
