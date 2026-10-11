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
