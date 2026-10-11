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

    @ParameterizedTest(name="{0} {1} {2} declared={3}")
    @CsvSource({
        "A1,POST,/api/auth/oauth/{provider},false",
        "A2,POST,/api/auth/refresh,false",
        "A3,POST,/api/auth/logout,true",
        "A4,GET,/api/news,true",
        "A5,GET,/api/news/{id},true",
        "A6,POST,/api/news/{id}/save,false",
        "A7,POST,/api/news/{id}/unsave,false",
        "A8,GET,/api/me,false",
        "A9,GET,/api/me/stats,false",
        "A10,GET,/api/news/{id}/body,false",
        "A11,POST,/api/quizzes/{quizId}/answer,false",
        "A12,POST,/api/terms/{id}/save,false",
        "A13,POST,/api/terms/{id}/unsave,false",
        "A14,POST,/api/news/{id}/read,true",
        "A15,GET,/api/news/search,false",
        "A16,GET,/api/me/saved-terms,false",
        "A17,GET,/api/me/saved-news,false",
        "A18,GET,/api/me/read-history,false",
        "A19,GET,/api/me/activity,false",
        "A20,GET,/api/categories,false",
        "A21,GET,/api/keywords,false",
        "A22,GET,/api/me/insights,false",
        "A23,GET,/api/search/trending,false",
        "A24,GET,/api/search/suggestions,false",
        "A25,PATCH,/api/me/profile,false",
        "A26,GET,/api/me/notification-settings,false",
        "A27,PATCH,/api/me/notification-settings,false",
        "A28,DELETE,/api/me,false",
        "A29,POST,/api/me/devices,false"
    })
    void inventoryAndAuthenticatedResponse(String id, String method, String path, boolean exists) throws Exception {
        assertThat(registered(method,path)).as(id+" exact controller registration").isEqualTo(exists);
        String concrete = path.replace("{provider}","google").replace("{id}",articleId.toString()).replace("{quizId}","1");
        var call = request(HttpMethod.valueOf(method),concrete).header("Authorization","Bearer "+access);
        if (!method.equals("GET")) call.contentType("application/json").content("{}");
        var result = mvc.perform(call).andReturn();
        int status = result.getResponse().getStatus();
        System.out.println("NOTION_AUDIT "+id+" "+method+" "+path+" registered="+exists+" HTTP="+status);
        if (exists) assertThat(status).isEqualTo(200);
        else assertThat(status).isGreaterThanOrEqualTo(400);
    }

    @Test void listContractDiffersFromNotion() throws Exception {
        var result = mvc.perform(get("/api/news").param("keyword",marker).param("page","1").param("limit","10"))
            .andExpect(status().isOk()).andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        assertThat(body.has("meta")).isFalse();
        assertThat(body.path("data").isObject()).isTrue();
        assertThat(body.at("/data/number").asInt()).isEqualTo(1);
        assertThat(body.at("/data/size").asInt()).isEqualTo(20);
        assertThat(body.at("/data/content").size()).isZero(); // page=1 is the second page
        var first = mvc.perform(get("/api/news").param("keyword",marker).param("page","0").param("size","10"))
            .andExpect(status().isOk()).andReturn();
        JsonNode item = json.readTree(first.getResponse().getContentAsString()).at("/data/content/0");
        assertThat(item.get("id").isNumber()).isTrue();
        assertThat(item.get("publishedAt").asText()).doesNotMatch(".*(Z|[+-]\\d{2}:\\d{2})$");
    }

    @Test void readIsNotIdempotentAndInflatesCount() throws Exception {
        for (int i=0;i<2;i++) mvc.perform(post("/api/news/"+articleId+"/read")
            .header("Authorization","Bearer "+access)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reading_history WHERE user_id=? AND article_id=?",
            Integer.class,userId,articleId)).isEqualTo(2);
        mvc.perform(get("/api/users/me/learning-stats").header("Authorization","Bearer "+access))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.readNewsCount").value(2))
            .andExpect(jsonPath("$.data.todayCompleted").doesNotExist());
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
