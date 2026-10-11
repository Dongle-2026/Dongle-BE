package com.youthletter;

import com.youthletter.global.auth.jwt.JwtTokenProvider;
import com.youthletter.domain.news.service.NewsService;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class MobileAuthIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired MockMvc mvc;
    @org.springframework.beans.factory.annotation.Autowired JwtTokenProvider tokens;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired NewsService news;
    @org.springframework.beans.factory.annotation.Autowired com.fasterxml.jackson.databind.ObjectMapper json;
    Long userId;
    @BeforeEach void setup() {
        String key = UUID.randomUUID().toString();
        userId = jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?, 'test', ?) RETURNING id",Long.class,key+"@test.invalid",key.substring(0,20));
    }
    @AfterEach void cleanup() { tokens.deleteRefreshToken(userId); }
    @Test void refreshIsNotAnAccessCredential() throws Exception {
        String token = tokens.createRefreshToken(userId);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/auth/withdraw")).andExpect(status().isUnauthorized());
    }
    @Test void tokenRotationProducesUniqueTokensAndRejectsOldRefresh() {
        String first = tokens.createRefreshToken(userId);
        String second = tokens.createRefreshToken(userId);
        assertThat(second).isNotEqualTo(first);
        assertThat(tokens.validateRefreshToken(userId,first)).isFalse();
        assertThat(tokens.validateRefreshToken(userId,second)).isTrue();
        assertThat(tokens.validateAccessToken(tokens.createAccessToken(userId,"test@test.invalid","USER"))).isTrue();
    }
    @Test void deletedAccountCannotReuseAccessToken() throws Exception {
        String token = tokens.createAccessToken(userId,"test@test.invalid","USER");
        jdbc.update("UPDATE users SET deleted_at=now() WHERE id=?",userId);
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
    }
    @Test void bookmarkSetAndRemoveAreIdempotent() {
        Long article = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?,'test','title','body','ECONOMY') RETURNING id",Long.class,UUID.randomUUID().toString());
        news.setBookmark(userId,article,true);
        news.setBookmark(userId,article,true);
        assertThat(news.isBookmarked(userId,article)).isTrue();
        news.setBookmark(userId,article,false);
        news.setBookmark(userId,article,false);
        assertThat(news.isBookmarked(userId,article)).isFalse();
    }

    @Test void notionAliasesPreserveAuthorizationAndIdempotency() throws Exception {
        String marker = UUID.randomUUID().toString();
        Long article = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?,'test',?,'body','YOUTH') RETURNING id", Long.class, marker, marker);
        String bearer = "Bearer " + tokens.createAccessToken(userId, "test@test.invalid", "USER");
        for (String path : java.util.List.of("/api/me", "/api/me/stats", "/api/me/saved-news")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
            mvc.perform(get(path).header("Authorization", bearer)).andExpect(status().isOk());
        }
        mvc.perform(get("/api/me").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.id").value(userId));
        mvc.perform(get("/api/me/stats").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.readNewsCount").value(0));
        for (String action : java.util.List.of("save", "unsave")) {
            mvc.perform(post("/api/news/"+article+"/"+action)).andExpect(status().isUnauthorized());
            for (int i=0; i<2; i++) mvc.perform(post("/api/news/"+article+"/"+action).header("Authorization", bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarked").value(action.equals("save")));
            mvc.perform(get("/api/me/saved-news").header("Authorization", bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(action.equals("save") ? 1 : 0));
        }
        mvc.perform(get("/api/news/search").param("q",marker)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(article));
        String refresh = tokens.createRefreshToken(userId);
        mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content(json.writeValueAsString(java.util.Map.of("refreshToken",refresh))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        assertThat(tokens.validateRefreshToken(userId,refresh)).isFalse();
    }

    @Test void appJourneySignupReadSaveReissueAndLogout() throws Exception {
        String key = UUID.randomUUID().toString();
        Long registeredId = null;
        try {
            String body = json.writeValueAsString(java.util.Map.of("email", key+"@test.invalid",
                    "password", "integration-test-password", "name", "통합테스트", "nickname", key.substring(0,20)));
            var signup = mvc.perform(post("/api/auth/signup").contentType("application/json").content(body))
                    .andExpect(status().isCreated()).andReturn();
            var session = json.readTree(signup.getResponse().getContentAsString()).get("data");
            String access = session.get("accessToken").asText();
            String refresh = session.get("refreshToken").asText();
            registeredId = tokens.getUserIdFromToken(access);
            Long article = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?,'test','title','body','YOUTH') RETURNING id",Long.class,key);
            String bearer = "Bearer " + access;
            mvc.perform(get("/api/news").param("keyword","title"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isArray());
            mvc.perform(get("/api/news/"+article+"/bookmark")).andExpect(status().isUnauthorized());
            mvc.perform(put("/api/news/"+article+"/bookmark").header("Authorization",bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarked").value(true));
            mvc.perform(put("/api/news/"+article+"/bookmark").header("Authorization",bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarked").value(true));
            mvc.perform(get("/api/users/me/bookmarks").header("Authorization",bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
            mvc.perform(post("/api/news/"+article+"/read").header("Authorization",bearer))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/users/me/learning-stats").header("Authorization",bearer))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.readNewsCount").value(1));
            var renewed = mvc.perform(post("/api/auth/reissue").contentType("application/json")
                    .content(json.writeValueAsString(java.util.Map.of("refreshToken",refresh))))
                    .andExpect(status().isOk()).andReturn();
            var next = json.readTree(renewed.getResponse().getContentAsString()).get("data");
            assertThat(tokens.validateRefreshToken(registeredId,refresh)).isFalse();
            String newAccess = next.get("accessToken").asText();
            mvc.perform(post("/api/auth/logout").header("Authorization","Bearer "+newAccess))
                    .andExpect(status().isOk());
            mvc.perform(get("/api/users/me").header("Authorization","Bearer "+newAccess))
                    .andExpect(status().isUnauthorized());
            assertThat(tokens.validateRefreshToken(registeredId,next.get("refreshToken").asText())).isFalse();
        } finally {
            if (registeredId != null) tokens.deleteRefreshToken(registeredId);
        }
    }
}
