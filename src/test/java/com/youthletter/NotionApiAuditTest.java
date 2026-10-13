package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 2026-10-08 Notion export against the current implementation.
 * Characterization audit: a passing test confirms the documented GAP, not specification compliance.
 * Test DB writes roll back; only this test's Redis keys are removed.
 */
@SpringBootTest(properties = {
    "crawler.cron=-", "policy.notification-cron=-", "ai-jobs.enabled=false",
    "spring.datasource.url=jdbc:postgresql://localhost:5433/youthletter_test",
    "spring.datasource.username=test", "spring.datasource.password=test1234",
    "spring.data.redis.host=localhost", "spring.data.redis.port=6380",
    "spring.jpa.hibernate.ddl-auto=validate"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="RUN_NOTION_API_AUDIT", matches="true")
class NotionApiAuditTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired JwtTokenProvider tokens;
    @Autowired StringRedisTemplate redis;
    @Autowired @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mappings;
    Long userId, articleId;
    String access, marker;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        marker = "audit-" + UUID.randomUUID();
        userId = jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'audit',?) RETURNING id",
            Long.class, marker + "@test.invalid", marker.substring(0,20));
        articleId = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'audit',?,'시험 본문','YOUTH',now()) RETURNING id",
            Long.class, marker, marker);
        access = tokens.createAccessToken(userId, marker + "@test.invalid", "USER");
    }
    @AfterEach void cleanup() {
        if (userId != null) tokens.deleteRefreshToken(userId);
        if (access != null) redis.delete("blacklist:" + access);
    }
    static String canonical(String path) { return path.replaceAll("\\{[^}]+}", "{id}"); }
    boolean registered(String method, String path) {
        return mappings.getHandlerMethods().keySet().stream().anyMatch(mapping ->
            mapping.getMethodsCondition().getMethods().stream().anyMatch(m -> m.name().equals(method))
            && mapping.getPatternValues().stream().anyMatch(p -> canonical(p).equals(canonical(path))));
    }

    Long quizId() {
        return jdbc.queryForObject("INSERT INTO quizzes(article_id,quiz_order,question,choices,answer_index,explanation,source_sentence_ids) VALUES (?,1,'q','[\"a\",\"b\",\"c\",\"d\"]'::jsonb,0,'e','[1]'::jsonb) RETURNING id",
            Long.class, articleId);
    }

    Long termId() {
        return jdbc.queryForObject("INSERT INTO financial_terms(name,path,definition,simple_definition) VALUES (?,'audit','시험 정의','쉬운 정의') RETURNING id",
            Long.class, marker + "-term");
    }

    @ParameterizedTest(name="{0} {1} {2} declared={3}")
    @CsvSource({
        "A1,POST,/api/auth/oauth/{provider},true",
        "A2,POST,/api/auth/refresh,true",
        "A3,POST,/api/auth/logout,true",
        "A4,GET,/api/news,true",
        "A5,GET,/api/news/{id},true",
        "A6,POST,/api/news/{id}/save,true",
        "A7,POST,/api/news/{id}/unsave,true",
        "A8,GET,/api/me,true",
        "A9,GET,/api/me/stats,true",
        "A10,GET,/api/news/{id}/body,true",
        "A11,POST,/api/quizzes/{quizId}/answer,true",
        "A12,POST,/api/terms/{id}/save,true",
        "A13,POST,/api/terms/{id}/unsave,true",
        "A14,POST,/api/news/{id}/read,true",
        "A15,GET,/api/news/search,true",
        "A16,GET,/api/me/saved-terms,true",
        "A17,GET,/api/me/saved-news,true",
        "A18,GET,/api/me/read-history,true",
        "A19,GET,/api/me/activity,true",
        "A20,GET,/api/categories,true",
        "A21,GET,/api/keywords,true",
        "A22,GET,/api/me/insights,true",
        "A23,GET,/api/search/trending,true",
        "A24,GET,/api/search/suggestions,true",
        "A25,PATCH,/api/me/profile,true",
        "A26,GET,/api/me/notification-settings,true",
        "A27,PATCH,/api/me/notification-settings,true",
        "A28,DELETE,/api/me,true",
        "A29,POST,/api/me/devices,true"
    })
    void inventoryAndAuthenticatedResponse(String id, String method, String path, boolean exists) throws Exception {
        assertThat(registered(method,path)).as(id+" exact controller registration").isEqualTo(exists);
        String resourceId = path.startsWith("/api/terms/") ? termId().toString() : articleId.toString();
        String concrete = path.replace("{provider}","google").replace("{id}",resourceId).replace("{quizId}", id.equals("A11") ? quizId().toString() : "1");
        var call = id.equals("A25")
            ? multipart(HttpMethod.PATCH, concrete).param("bio", "audit").header("Authorization","Bearer "+access)
            : request(HttpMethod.valueOf(method),concrete).header("Authorization","Bearer "+access);
        if (!method.equals("GET") && !id.equals("A25")) call.contentType("application/json").content("{}");
        if (id.equals("A2")) call.content(json.writeValueAsString(java.util.Map.of("refreshToken", tokens.createRefreshToken(userId))));
        if (id.equals("A15") || id.equals("A24")) call.param("q", marker);
        if (id.equals("A11")) call.content("{\"selectedIndex\":0}");
        if (id.equals("A29")) call.content("{\"deviceId\":\"audit\",\"token\":\""+marker+"\",\"platform\":\"IOS\"}");
        var result = mvc.perform(call).andReturn();
        int status = result.getResponse().getStatus();
        System.out.println("NOTION_AUDIT "+id+" "+method+" "+path+" registered="+exists+" HTTP="+status);
        // A1은 실제 제공자 idToken이 필요하므로 경로 도달(빈 본문 검증 400)만 확인. 서명 검증은 MobileOAuthIdTokenIntegrationTest.
        if (id.equals("A1")) assertThat(status).isEqualTo(400);
        else if (exists) assertThat(status).isEqualTo(200);
        else assertThat(status).isGreaterThanOrEqualTo(400);
    }

    @Test void limitSelectsAppFeedWhileSizePreservesLegacyPage() throws Exception {
        var result = mvc.perform(get("/api/news").param("keyword",marker).param("page","1").param("limit","10"))
            .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("meta")).isTrue();
        assertThat(body.path("data").isArray()).isTrue();
        assertThat(body.at("/meta/page").asInt()).isEqualTo(1);
        assertThat(body.at("/meta/limit").asInt()).isEqualTo(10);
        assertThat(body.path("data").size()).isEqualTo(1);
        assertThat(body.at("/data/0/id").isTextual()).isTrue();
        assertThat(body.at("/data/0/slides").isArray()).isTrue();
        var first = mvc.perform(get("/api/news").param("keyword",marker).param("page","0").param("size","10"))
            .andExpect(status().isOk()).andReturn();
        JsonNode item = json.readTree(first.getResponse().getContentAsString()).at("/data/content/0");
        assertThat(item.get("id").isNumber()).isTrue();
        assertThat(item.get("publishedAt").asText()).doesNotMatch(".*(Z|[+-]\\d{2}:\\d{2})$");
    }

    @Test void readIsIdempotentPerKoreanDay() throws Exception {
        for (int i=0;i<2;i++) mvc.perform(post("/api/news/"+articleId+"/read")
            .header("Authorization","Bearer "+access)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reading_history WHERE user_id=? AND article_id=?",
            Integer.class,userId,articleId)).isEqualTo(1);
        mvc.perform(get("/api/users/me/learning-stats").header("Authorization","Bearer "+access))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.readNewsCount").value(1))
            .andExpect(jsonPath("$.data.todayCompleted").value(true)); // A9 확장: 오늘 읽음 → 학습 완료
    }

    @Test void existingAlternativesWorkButAreNotAliases() throws Exception {
        String bearer="Bearer "+access;
        mvc.perform(get("/api/users/me").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(userId));
        mvc.perform(patch("/api/users/me").header("Authorization",bearer).contentType("application/json")
            .content("{\"nickname\":\""+marker.substring(0,18)+"x\"}")).andExpect(status().isOk());
        for(int i=0;i<2;i++) mvc.perform(put("/api/news/"+articleId+"/bookmark").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarked").value(true));
        mvc.perform(get("/api/users/me/bookmarks").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        for(int i=0;i<2;i++) mvc.perform(delete("/api/news/"+articleId+"/bookmark").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.bookmarked").value(false));
        mvc.perform(get("/api/users/me/learned-terms").header("Authorization",bearer))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(put("/api/users/me/devices").header("Authorization",bearer).contentType("application/json")
            .content("{\"deviceId\":\"audit\",\"token\":\""+marker+"\",\"platform\":\"IOS\"}"))
            .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_tokens WHERE user_id=?",Integer.class,userId)).isEqualTo(1);
    }

    @Test void errorEnvelopeAndPublicAccessDiffer() throws Exception {
        mvc.perform(get("/api/news")).andExpect(status().isOk()); // Notion says protected
        mvc.perform(get("/api/news/"+articleId)).andExpect(status().isOk());
        mvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(4001)).andExpect(jsonPath("$.error").doesNotExist());
        mvc.perform(get("/api/news").param("date","invalid")).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(4105)).andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test void learnIsNotAnIndependentTermBookmark() throws Exception {
        Long term = jdbc.queryForObject("INSERT INTO financial_terms(name,path,definition,simple_definition) VALUES (?,'audit','시험 정의','쉬운 정의') RETURNING id",
            Long.class,marker);
        for (int i=0;i<2;i++) mvc.perform(post("/api/terms/"+term+"/learn")
            .header("Authorization","Bearer "+access)).andExpect(status().isOk());
        mvc.perform(get("/api/users/me/learned-terms").header("Authorization","Bearer "+access))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1))
            .andExpect(jsonPath("$.data[0].termId").value(term));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM term_lookup_log WHERE user_id=? AND term_id=?",
            Integer.class,userId,term)).isEqualTo(2);
    }

    @Test void reissueAndWithdrawAlternativesWork() throws Exception {
        String refresh=tokens.createRefreshToken(userId);
        var result=mvc.perform(post("/api/auth/reissue").contentType("application/json")
            .content(json.writeValueAsString(java.util.Map.of("refreshToken",refresh))))
            .andExpect(status().isOk()).andReturn();
        JsonNode data=json.readTree(result.getResponse().getContentAsString()).get("data");
        assertThat(data.get("accessToken").asText()).isNotBlank();
        assertThat(tokens.validateRefreshToken(userId,refresh)).isFalse();
        access=data.get("accessToken").asText();
        mvc.perform(delete("/api/auth/withdraw").header("Authorization","Bearer "+access))
            .andExpect(status().isOk());
        mvc.perform(get("/api/users/me").header("Authorization","Bearer "+access))
            .andExpect(status().isUnauthorized());
    }
}
