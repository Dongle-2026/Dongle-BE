package com.youthletter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class ContentReportIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    Long user, other, article, quiz, card;
    String auth, admin, otherAuth;

    @BeforeEach void setup() {
        user=user("USER"); other=user("USER"); Long administrator=user("ADMIN");
        auth="Bearer "+tokens.createAccessToken(user, "report@test.invalid", "USER");
        otherAuth="Bearer "+tokens.createAccessToken(other, "other@test.invalid", "USER");
        admin="Bearer "+tokens.createAccessToken(administrator, "admin@test.invalid", "ADMIN");
        article=jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?,'test','신고 시험','시험 본문','금융') RETURNING id", Long.class, UUID.randomUUID().toString());
        quiz=jdbc.queryForObject("INSERT INTO quizzes(article_id,quiz_order,question,choices,answer_index,explanation,source_sentence_ids) VALUES (?,1,'질문','[\"가\",\"나\",\"다\",\"라\"]',0,'설명','[1]') RETURNING id",Long.class,article);
        card=jdbc.queryForObject("INSERT INTO card_news(article_id,total_cards,status) VALUES (?,0,'PUBLISHED') RETURNING id", Long.class, article);
    }
    Long user(String role) {
        String marker=UUID.randomUUID().toString();
        return jdbc.queryForObject("INSERT INTO users(email,name,nickname,role) VALUES (?,'test',?,?) RETURNING id", Long.class,marker+"@test.invalid",marker.substring(0,20),role);
    }
    long report(String path, String bearer) throws Exception {
        var result=mvc.perform(post(path).header("Authorization",bearer).contentType("application/json")
                .content("{\"reason\":\"INCORRECT_CONTENT\",\"description\":\"근거와 다릅니다\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.userId").doesNotExist()).andReturn();
        return json.readTree(result.getResponse().getContentAsString()).at("/data/id").asLong();
    }
    @Test void duplicateReportIsIdempotentAndUsersAreIndependent() throws Exception {
        long first=report("/api/quizzes/"+quiz+"/reports",auth);
        assertThat(report("/api/quizzes/"+quiz+"/reports",auth)).isEqualTo(first);
        assertThat(report("/api/quizzes/"+quiz+"/reports",otherAuth)).isNotEqualTo(first);
        report("/api/card-news/"+card+"/reports",auth);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM content_reports WHERE quiz_id=?",Integer.class,quiz)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM card_news WHERE id=?",String.class,card)).isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM honey_ledger WHERE user_id=?",Integer.class,user)).isZero();
    }
    @Test void onlyAdminsCanListAndChangeStatusAndRetryCannotReopen() throws Exception {
        long id=report("/api/quizzes/"+quiz+"/reports",auth);
        mvc.perform(get("/api/admin/content-reports").header("Authorization",auth)).andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/content-reports/"+id).header("Authorization",auth).contentType("application/json").content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/content-reports").param("status","OPEN").header("Authorization",admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(id));
        mvc.perform(patch("/api/admin/content-reports/"+id).header("Authorization",admin).contentType("application/json").content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVIEWED"));
        mvc.perform(post("/api/quizzes/"+quiz+"/reports").header("Authorization",auth).contentType("application/json").content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REVIEWED"));
        assertThat(jdbc.queryForObject("SELECT reason FROM content_reports WHERE id=?",String.class,id)).isEqualTo("INCORRECT_CONTENT");
    }
    @Test void myReportsArePrivateFilteredAndPaginated() throws Exception {
        long mine=report("/api/quizzes/"+quiz+"/reports",auth);
        report("/api/card-news/"+card+"/reports",auth);
        report("/api/quizzes/"+quiz+"/reports",otherAuth);
        jdbc.update("UPDATE content_reports SET status='REVIEWED' WHERE id=?",mine);
        mvc.perform(get("/api/me/content-reports")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/content-reports").header("Authorization",auth).param("status","REVIEWED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(mine))
                .andExpect(jsonPath("$.data.content[0].userId").doesNotExist());
        mvc.perform(get("/api/me/content-reports").header("Authorization",auth).param("size","1").param("page","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1));
        mvc.perform(get("/api/me/content-reports").header("Authorization",otherAuth).param("status","REVIEWED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/me/content-reports").header("Authorization",auth).param("status","INVALID"))
                .andExpect(status().isBadRequest());
    }

    @Test void rejectsAnonymousMissingTargetsAndInvalidInput() throws Exception {
        mvc.perform(post("/api/quizzes/"+quiz+"/reports").contentType("application/json").content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isUnauthorized());
        for(String body:new String[]{"{}","{\"reason\":\"UNKNOWN\"}","{\"reason\":\"OTHER\",\"description\":\""+"가".repeat(1001)+"\"}"})
            mvc.perform(post("/api/quizzes/"+quiz+"/reports").header("Authorization",auth).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/quizzes/9223372036854775807/reports").header("Authorization",auth).contentType("application/json").content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/card-news/9223372036854775807/reports").header("Authorization",auth).contentType("application/json").content("{\"reason\":\"OTHER\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(patch("/api/admin/content-reports/9223372036854775807").header("Authorization",admin).contentType("application/json").content("{\"status\":\"DISMISSED\"}"))
                .andExpect(status().isNotFound());
    }
}
