package com.youthletter;

import com.youthletter.domain.news.service.AiJobService;
import com.youthletter.domain.news.client.dto.AiCardResponse;
import com.youthletter.domain.cardnews.entity.CardTemplateType;
import com.youthletter.global.exception.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties="crawler.cron=-")
@ActiveProfiles("test")
@Transactional
class AiJobIntegrationTest {
    @org.springframework.beans.factory.annotation.Autowired AiJobService jobs;
    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    Long article;
    @Test void bundleStoresAllOutputsAndCompletesSiblingJobs() {
        String content = "예금 적금 대출 금리 신용도에 관한 기사입니다.";
        jdbc.update("UPDATE news_articles SET content=? WHERE id=?", content, article);
        for (var kind : AiJobService.Kind.values()) jobs.enqueue(article, kind);
        var claim = jobs.claim().orElseThrow();
        var terms = List.of("예금", "적금", "대출", "금리", "신용도").stream().map(t ->
                new com.youthletter.domain.news.client.dto.AiProcessResponse.TermDto(t, "학습용 설명", content.indexOf(t), content.indexOf(t)+t.length())).toList();
        var analysis = new com.youthletter.domain.news.client.dto.AiProcessResponse("기사 요약",terms,
                Collections.nCopies(768,0.1f));
        var cards = new AiCardResponse("test-model","card-fast",java.util.stream.IntStream.rangeClosed(1,3)
                .mapToObj(i->new AiCardResponse.Card(i,CardTemplateType.T01,"제목",content,null,null,List.of(1))).toList());
        var quiz = new com.youthletter.domain.news.client.dto.AiQuizResponse("test-model","quiz-fast",
                java.util.stream.IntStream.rangeClosed(1,3).mapToObj(i->
                    new com.youthletter.domain.news.client.dto.AiQuizResponse.Item("빈칸 문제 "+i,
                            List.of("예금","적금","대출","금리"),0,"원문 표현은 예금입니다.",List.of(1))).toList());
        var bundle = new com.youthletter.domain.news.client.dto.AiBundleResponse(analysis,cards,quiz,"test-model");
        assertThat(jobs.completeBundle(claim,bundle)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ai_jobs WHERE article_id=? AND status='SUCCEEDED'",Integer.class,article)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM article_term_highlights WHERE article_id=?",Integer.class,article)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quizzes WHERE article_id=?",Integer.class,article)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM news_summaries WHERE article_id=? AND valid_to IS NULL",Integer.class,article)).isEqualTo(1);
        assertThat(jobs.completeBundle(claim,bundle)).isFalse();
    }
    @BeforeEach void setup() {
        article = jdbc.queryForObject("INSERT INTO news_articles(source_url,source_name,title,content,category) VALUES (?,'test','test','본문','YOUTH') RETURNING id",
                Long.class, "https://example.invalid/" + UUID.randomUUID());
    }
    @Test void duplicateSubmissionReturnsExistingActiveJob() {
        var first = jobs.enqueue(article, AiJobService.Kind.CARD);
        assertThat(jobs.enqueue(article, AiJobService.Kind.CARD).id()).isEqualTo(first.id());
        assertThat(jobs.enqueue(article, AiJobService.Kind.ARTICLE).id()).isNotEqualTo(first.id());
        var claim = jobs.claim().orElseThrow();
        assertThat(claim.job().attempts()).isEqualTo(1);
        assertThat(jobs.claim()).isEmpty();
    }
    @Test void expiredLeaseRecoversButRejectsStaleResult() {
        var job = jobs.enqueue(article, AiJobService.Kind.CARD);
        var old = jobs.claim().orElseThrow();
        jdbc.update("UPDATE ai_jobs SET lease_until=now()-interval '1 second' WHERE id=?", job.id());
        var recovered = jobs.claim().orElseThrow();
        assertThat(recovered.token()).isNotEqualTo(old.token());
        assertThat(recovered.job().attempts()).isEqualTo(2);
        assertThat(jobs.complete(old, null, null, "model")).isFalse();
        assertThat(jobs.get(job.id()).status()).isEqualTo("RUNNING");
    }
    @Test void retriesAreBoundedAndDelayed() {
        var job = jobs.enqueue(article, AiJobService.Kind.CARD);
        for (int attempt=1; attempt<=3; attempt++) {
            var claim = jobs.claim().orElseThrow();
            jobs.fail(claim, ErrorCode.AI_SERVICE_TIMEOUT);
            assertThat(jobs.claim()).isEmpty();
            jdbc.update("UPDATE ai_jobs SET available_at=now()-interval '1 second' WHERE id=?", job.id());
        }
        assertThat(jobs.get(job.id()).status()).isEqualTo("FAILED");
        assertThat(jobs.get(job.id()).attempts()).isEqualTo(3);
        assertThat(jobs.enqueue(article, AiJobService.Kind.CARD).id()).isNotEqualTo(job.id());
    }
    @Test void completionStoresCardsAndTerminalStatus() {
        var job = jobs.enqueue(article, AiJobService.Kind.CARD);
        var claim = jobs.claim().orElseThrow();
        var cards = new AiCardResponse("test-model", "v1", java.util.stream.IntStream.rangeClosed(1,5)
                .mapToObj(i -> new AiCardResponse.Card(i, CardTemplateType.T01,"제목","본문",null,null,List.of(1))).toList());
        assertThat(jobs.complete(claim, null, cards, "model")).isTrue();
        assertThat(jobs.get(job.id()).status()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM card_news WHERE article_id=?", Integer.class, article)).isEqualTo(1);
        assertThat(jobs.complete(claim, null, cards, "model")).isFalse();
    }
}
