package com.youthletter;

import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.service.NewsAiWriter;
import com.youthletter.domain.cardnews.service.*;
import com.youthletter.domain.cardnews.repository.CardNewsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** 명시적으로 실행한 경우에만 실제 로컬 모델을 호출한다. 테스트 DB 데이터는 rollback한다. */
@SpringBootTest(properties={"crawler.cron=-", "policy.notification-cron=-", "ai-jobs.enabled=false",
        "ai-service.use-stub=false", "ai-service.base-url=${LIVE_AI_URL:http://127.0.0.1:3002}"})
@ActiveProfiles("test")
@Transactional
@EnabledIfEnvironmentVariable(named="RUN_AI_SMOKE", matches="true")
class LiveAiIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired AiServiceClient client;
    @org.springframework.beans.factory.annotation.Autowired NewsAiWriter newsWriter;
    @org.springframework.beans.factory.annotation.Autowired CardNewsWriter cardWriter;
    @org.springframework.beans.factory.annotation.Autowired CardNewsValidator validator;
    @org.springframework.beans.factory.annotation.Autowired CardNewsRepository cards;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @jakarta.persistence.PersistenceContext jakarta.persistence.EntityManager em;
    @org.springframework.beans.factory.annotation.Autowired com.youthletter.domain.quiz.service.QuizValidator quizValidator;
    @org.springframework.beans.factory.annotation.Autowired com.youthletter.domain.quiz.service.QuizWriter quizWriter;
    @org.springframework.beans.factory.annotation.Autowired com.youthletter.domain.quiz.service.QuizService quizService;
    @org.springframework.beans.factory.annotation.Autowired com.youthletter.domain.crawler.service.HankyungCrawlerService crawler;
    @org.springframework.beans.factory.annotation.Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test void liveCrawledArticleRoundTrip() throws Exception {
        java.util.List<String> urls = org.springframework.test.util.ReflectionTestUtils.invokeMethod(crawler, "collectArticleUrls");
        assertThat(urls).isNotEmpty();
        // One article only: do not crawl the entire feed or retry a rate-limited publisher.
        com.youthletter.domain.crawler.dto.CrawledArticleDto article =
                org.springframework.test.util.ReflectionTestUtils.invokeMethod(crawler, "parseArticle", urls.get(0));
        assertThat(article).isNotNull();
        assertThat(article.title()).isNotBlank();
        assertThat(article.content().length()).isGreaterThan(200);
        roundTrip(new AiProcessRequest(article.url(), article.title(), article.content(), article.category()), "crawled");
    }

    @Test void springNodeOllamaPostgresRoundTrip() throws Exception {
        String content = "이 문서는 실제 뉴스가 아닌 연동 시험용 자료입니다. 예산은 일정 기간 사용할 금액을 계획하는 것입니다. 저축은 사용하지 않은 돈을 보관하는 행동입니다. 청년 금융 교육에서는 소비를 기록하고 정기적으로 확인하는 습관을 설명합니다. 참여자는 자신의 지출 계획을 점검할 수 있습니다.";
        String url = "https://example.invalid/" + UUID.randomUUID();
        roundTrip(new AiProcessRequest(url, "금융 교육 시험", content, "YOUTH"), "fixture");
    }

    private void roundTrip(AiProcessRequest request, String label) throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo("youthletter_test");
        var report = new java.util.LinkedHashMap<String, Object>();
        report.put("input", request);
        java.nio.file.Path output = java.nio.file.Path.of("build/reports/live-ai", label + ".json");
        java.nio.file.Files.createDirectories(output.getParent());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        Long id = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?, 'live-test',?,?,?) RETURNING id", Long.class,
                "https://example.invalid/live-test/" + UUID.randomUUID(), request.title(), request.content(), request.category());
        long started = System.nanoTime();
        var processed = client.process(request);
        report.put("processMs", (System.nanoTime() - started) / 1_000_000);
        report.put("summary", processed.summary());
        report.put("terms", processed.terms());
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        assertThat(processed.embedding()).hasSize(768);
        newsWriter.replace(id, processed, "qwen3:8b");
        em.flush();
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM news_articles WHERE id=?", Integer.class, id)).isEqualTo(768);
        started = System.nanoTime();
        var generated = client.generateCards(request);
        report.put("cardsMs", (System.nanoTime() - started) / 1_000_000);
        report.put("cards", generated);
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        validator.validate(generated, request.content());
        cardWriter.save(id, generated);
        em.flush(); em.clear();
        assertThat(cards.findByArticleId(id).orElseThrow().getCards()).hasSizeBetween(5,7);
        started = System.nanoTime();
        var quiz = client.generateQuiz(request);
        report.put("quizMs", (System.nanoTime() - started) / 1_000_000);
        report.put("quiz", quiz);
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        quizValidator.validate(quiz, request.content());
        assertThat(quizWriter.saveIfAbsent(id, quiz)).isTrue();
        assertThat(quizWriter.saveIfAbsent(id, quiz)).isFalse();
        String marker = UUID.randomUUID().toString();
        Long userId = jdbc.queryForObject("INSERT INTO users(email,name,nickname) VALUES (?, 'live-test', ?) RETURNING id",
                Long.class, marker + "@example.invalid", marker.substring(0, 20));
        var body = quizService.body(id, userId);
        assertThat(body.quizStatus()).isEqualTo("READY");
        assertThat(body.quizzes()).hasSize(3);
        String publicJson = json.writeValueAsString(body);
        assertThat(publicJson).doesNotContain("answerIndex", "explanation");
        for (Long quizId : jdbc.queryForList("SELECT id FROM quizzes WHERE article_id=? ORDER BY quiz_order", Long.class, id)) {
            int correct = jdbc.queryForObject("SELECT answer_index FROM quizzes WHERE id=?", Integer.class, quizId);
            var wrong = quizService.answer(userId, quizId, new com.youthletter.domain.quiz.dto.QuizAnswerRequest((correct + 1) % 4, null));
            assertThat(wrong.isCorrect()).isFalse();
            assertThat(wrong.earnedHoney()).isZero();
            assertThat(wrong.correctIndex()).isNull();
            var answer = new com.youthletter.domain.quiz.dto.QuizAnswerRequest(correct, null);
            assertThat(quizService.answer(userId, quizId, answer).earnedHoney()).isEqualTo(10);
            assertThat(quizService.answer(userId, quizId, answer).earnedHoney()).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT sum(amount) FROM honey_ledger WHERE user_id=?", Long.class, userId)).isEqualTo(30);
        report.put("persistenceAndScoring", "PASS (transaction rolled back after test)");
        json.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
    }
}
