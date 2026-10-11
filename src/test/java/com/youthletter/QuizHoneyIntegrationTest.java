package com.youthletter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.global.auth.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 노션 A10 본문, A11 퀴즈 제출, A19 히트맵, A22 인사이트, A9 통계 확장, 꿀 지급 규칙. */
@SpringBootTest(properties = "crawler.cron=-") @ActiveProfiles("test") @AutoConfigureMockMvc @Transactional
class QuizHoneyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenProvider tokens;
    @Autowired ObjectMapper json;
    Long userId, otherId, articleId, termId;
    String bearer, otherBearer, m;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        m = UUID.randomUUID().toString().substring(0, 8);
        userId = user("q" + m); otherId = user("o" + m);
        bearer = "Bearer " + tokens.createAccessToken(userId, "q" + m + "@test.invalid", "USER");
        otherBearer = "Bearer " + tokens.createAccessToken(otherId, "o" + m + "@test.invalid", "USER");
        articleId = article("투자", "코스피가 상승했다. ETF 자금이 유입됐다. 개인 투자자가 늘었다. 증권사는 신중론을 폈다.");
        termId = jdbc.queryForObject("INSERT INTO financial_terms(name,path,definition,simple_definition) VALUES (?, 'finance.stock', '정의', '쉬운 정의') RETURNING id", Long.class, "ETF" + m);
        jdbc.update("INSERT INTO article_term_highlights(article_id,term_id,position_start,position_end) VALUES (?,?,11,14),(?,?,30,33)", articleId, termId, articleId, termId);
    }
    Long user(String key) {
        return jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?,'t',?) RETURNING id", Long.class, key + "@test.invalid", key);
    }
    Long article(String category, String content) {
        return jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category,published_at) VALUES (?,'한경',?,?,?,now()) RETURNING id",
                Long.class, "q-" + UUID.randomUUID(), "기사 " + m, content, category);
    }
    Long quiz(Long article, int order, int answer) {
        return jdbc.queryForObject("""
                INSERT INTO quizzes(article_id,quiz_order,question,choices,answer_index,explanation,source_sentence_ids)
                VALUES (?,?,?,'["가","나","다","라"]'::jsonb,?, '해설입니다', '[1]'::jsonb) RETURNING id
                """, Long.class, article, order, "질문" + order, answer);
    }
    ResultActions answer(String auth, Long quizId, String body) throws Exception {
        var req = post("/api/quizzes/" + quizId + "/answer").contentType("application/json").content(body);
        return mvc.perform(auth == null ? req : req.header("Authorization", auth));
    }
    JsonNode data(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString()).get("data");
    }
    int ledger(Long user) { return jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM honey_ledger WHERE user_id=?", Integer.class, user); }

    @Test void bodyWithoutQuizzesReportsStatusAndBuildsSectionsAndTerms() throws Exception {
        JsonNode body = data(mvc.perform(get("/api/news/" + articleId + "/body")).andExpect(status().isOk()));
        assertThat(body.get("quizStatus").asText()).isEqualTo("UNAVAILABLE"); // 테스트 프로필은 AI 작업자 꺼짐
        assertThat(body.get("quizzes").size()).isZero();
        assertThat(body.hasNonNull("intro")).isFalse(); // 요약 없으면 만들지 않음(null 필드는 생략됨)
        assertThat(body.get("sections").size()).isEqualTo(2); // 4문장 → 3+1
        assertThat(body.at("/sections/0/id").asText()).isEqualTo("s1");
        assertThat(body.get("terms").size()).isEqualTo(1); // 같은 용어 중복 하이라이트는 1개
        assertThat(body.at("/terms/0/termId").asLong()).isEqualTo(termId);
        assertThat(body.at("/terms/0/category").asText()).isEqualTo("금융");
        assertThat(body.at("/terms/0").has("saved")).isFalse(); // 비로그인
        assertThat(body.get("source").asText()).isEqualTo("한경");
        mvc.perform(get("/api/news/999999999/body")).andExpect(status().isNotFound());
    }

    @Test void bodyNeverLeaksAnswersAndShowsMyResults() throws Exception {
        Long q1 = quiz(articleId, 1, 2), q2 = quiz(articleId, 2, 0);
        mvc.perform(post("/api/terms/" + termId + "/save").header("Authorization", bearer)).andExpect(status().isOk());
        answer(bearer, q1, "{\"selectedIndex\":0}").andExpect(status().isOk());
        answer(bearer, q1, "{\"choiceId\":\"c\"}").andExpect(status().isOk());
        String raw = mvc.perform(get("/api/news/" + articleId + "/body").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("answerIndex").doesNotContain("해설입니다").doesNotContain("correctIndex");
        JsonNode body = json.readTree(raw).get("data");
        assertThat(body.get("quizStatus").asText()).isEqualTo("READY");
        assertThat(body.at("/quizzes/0/id").asLong()).isEqualTo(q1);
        assertThat(body.at("/quizzes/0/choices/2/id").asText()).isEqualTo("c");
        assertThat(body.at("/quizzes/0/choices/2/text").asText()).isEqualTo("다");
        assertThat(body.at("/quizzes/0/honey").asInt()).isEqualTo(10);
        assertThat(body.at("/quizzes/0/myResult/attempts").asInt()).isEqualTo(2);
        assertThat(body.at("/quizzes/0/myResult/solved").asBoolean()).isTrue();
        assertThat(body.at("/quizzes/0/myResult/earnedHoney").asInt()).isEqualTo(10);
        assertThat(body.at("/quizzes/1/myResult/attempts").asInt()).isZero();
        assertThat(body.at("/terms/0/saved").asBoolean()).isTrue();
        JsonNode other = data(mvc.perform(get("/api/news/" + articleId + "/body").header("Authorization", otherBearer)));
        assertThat(other.at("/quizzes/0/myResult/attempts").asInt()).isZero(); // 다른 사용자 결과 비노출
        assertThat(other.at("/terms/0/saved").asBoolean()).isFalse();
        assertThat(q2).isNotNull();
    }

    @Test void retryAllowedButHoneyOnlyForFirstCorrect() throws Exception {
        Long q = quiz(articleId, 1, 1);
        JsonNode wrong = data(answer(bearer, q, "{\"selectedIndex\":3}").andExpect(status().isOk()));
        assertThat(wrong.get("isCorrect").asBoolean()).isFalse();
        assertThat(wrong.get("earnedHoney").asInt()).isZero();
        assertThat(wrong.has("correctIndex") && !wrong.get("correctIndex").isNull()).isFalse(); // 오답 시 정답 비공개
        assertThat(wrong.has("explanation") && !wrong.get("explanation").isNull()).isFalse();

        JsonNode right = data(answer(bearer, q, "{\"choiceId\":\"B\"}").andExpect(status().isOk()));
        assertThat(right.get("isCorrect").asBoolean()).isTrue();
        assertThat(right.get("earnedHoney").asInt()).isEqualTo(10);
        assertThat(right.get("correctIndex").asInt()).isEqualTo(1);
        assertThat(right.get("explanation").asText()).isEqualTo("해설입니다");
        assertThat(right.get("alreadySolved").asBoolean()).isFalse();

        JsonNode again = data(answer(bearer, q, "{\"selectedIndex\":1}").andExpect(status().isOk()));
        assertThat(again.get("earnedHoney").asInt()).isZero();
        assertThat(again.get("alreadySolved").asBoolean()).isTrue();
        assertThat(right.get("totalHoney").asLong()).isEqualTo(10);
        assertThat(again.get("totalHoney").asLong()).isEqualTo(10);
        assertThat(wrong.get("totalHoney").asLong()).isZero();
        assertThat(ledger(userId)).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quiz_attempts WHERE user_id=?", Integer.class, userId)).isEqualTo(3);
        assertThat(data(answer(otherBearer, q, "{\"selectedIndex\":1}")).get("earnedHoney").asInt()).isEqualTo(10); // 사용자별
    }

    @Test void invalidAnswersAreRejected() throws Exception {
        Long q = quiz(articleId, 1, 0);
        answer(bearer, q, "{}").andExpect(status().isBadRequest());
        answer(bearer, q, "{\"selectedIndex\":0,\"choiceId\":\"a\"}").andExpect(status().isBadRequest());
        answer(bearer, q, "{\"selectedIndex\":4}").andExpect(status().isBadRequest());
        answer(bearer, q, "{\"choiceId\":\"e\"}").andExpect(status().isBadRequest());
        answer(bearer, 999999999L, "{\"selectedIndex\":0}").andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(4203));
        answer(null, q, "{\"selectedIndex\":0}").andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quiz_attempts WHERE quiz_id=?", Integer.class, q)).isZero();
    }

    @Test void readingPaysFiveHoneyOncePerArticlePerDayAndFeedsStatsHeatmapHistory() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(post("/api/news/" + articleId + "/read").header("Authorization", bearer)).andExpect(status().isOk());
        assertThat(ledger(userId)).isEqualTo(5);
        Long q = quiz(articleId, 1, 0);
        answer(bearer, q, "{\"selectedIndex\":0}").andExpect(status().isOk());
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("INSERT INTO honey_ledger(user_id,reason,article_id,amount,earned_on) VALUES (?, 'ARTICLE_READ', ?, 45, ?)", userId, articleId, today.minusDays(2));

        mvc.perform(get("/api/me/stats").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.todayCompleted").value(true))
                .andExpect(jsonPath("$.data.totalHoney").value(60))
                .andExpect(jsonPath("$.data.totalQuizCount").value(1))
                .andExpect(jsonPath("$.data.readNewsCount").value(1))
                .andExpect(jsonPath("$.data.todayHoney").value(15)) // 2일 전 45꿀은 제외
                .andExpect(jsonPath("$.data.correctCount").value(1));
        mvc.perform(get("/api/me/stats").header("Authorization", otherBearer))
                .andExpect(jsonPath("$.data.todayCompleted").value(false)).andExpect(jsonPath("$.data.totalHoney").value(0));

        JsonNode days = data(mvc.perform(get("/api/me/activity").param("days", "7").header("Authorization", bearer)).andExpect(status().isOk()));
        assertThat(days.size()).isEqualTo(7);
        assertThat(days.get(6).get("date").asText()).isEqualTo(today.toString());
        assertThat(days.get(6).get("honey").asInt()).isEqualTo(15);
        assertThat(days.get(6).get("level").asInt()).isEqualTo(2);
        assertThat(days.get(4).get("honey").asInt()).isEqualTo(45);
        assertThat(days.get(4).get("level").asInt()).isEqualTo(4);
        assertThat(days.get(5).get("level").asInt()).isZero();
        assertThat(data(mvc.perform(get("/api/me/activity").param("days", "9999").header("Authorization", bearer))).size()).isEqualTo(365);

        mvc.perform(get("/api/me/read-history").header("Authorization", bearer))
                .andExpect(jsonPath("$.data.content[0].articleId").value(articleId))
                .andExpect(jsonPath("$.data.content[0].honey").value(60));
        mvc.perform(get("/api/me/activity")).andExpect(status().isUnauthorized());
    }

    @Test void insightsUseFirstAttemptsAndRecentReads() throws Exception {
        mvc.perform(get("/api/me/insights").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctRate").doesNotExist())
                .andExpect(jsonPath("$.data.totalQuizCount").value(0))
                .andExpect(jsonPath("$.data.weakCategories.length()").value(0));
        Long economy = article("경제", "물가가 올랐다.");
        Long[] invest = {quiz(articleId, 1, 0), quiz(articleId, 2, 0), quiz(articleId, 3, 0)};
        Long econQuiz = quiz(economy, 1, 0);
        for (Long q : invest) { answer(bearer, q, "{\"selectedIndex\":1}"); answer(bearer, q, "{\"selectedIndex\":0}"); } // 첫 시도 오답
        answer(bearer, econQuiz, "{\"selectedIndex\":0}");
        for (int i = 0; i < 2; i++) mvc.perform(post("/api/news/" + economy + "/read").header("Authorization", bearer));
        mvc.perform(post("/api/news/" + articleId + "/read").header("Authorization", bearer));
        mvc.perform(get("/api/me/insights").header("Authorization", bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctRate").value(25)) // 4문제 중 첫 시도 정답 1
                .andExpect(jsonPath("$.data.totalQuizCount").value(4))
                .andExpect(jsonPath("$.data.weakCategories[0]").value("투자"))
                .andExpect(jsonPath("$.data.weakCategories.length()").value(1)) // 경제는 3문제 미만
                .andExpect(jsonPath("$.data.interestedCategories.length()").value(2));
    }
}
