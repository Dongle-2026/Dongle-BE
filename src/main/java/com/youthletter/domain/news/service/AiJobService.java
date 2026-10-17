package com.youthletter.domain.news.service;

import com.youthletter.domain.cardnews.service.CardNewsWriter;
import com.youthletter.domain.news.client.dto.*;
import com.youthletter.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** DB를 영속 대기열로 사용한다. 모델 호출은 이 서비스의 트랜잭션 밖에서 수행한다. */
@Service
@RequiredArgsConstructor
public class AiJobService {
    public enum Kind { ARTICLE, CARD, QUIZ }
    public record Job(Long id, Long articleId, Kind kind, String status, int attempts,
                      String errorCode, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Claim(Job job, String token) {}
    private final JdbcTemplate jdbc;
    private final NewsAiWriter newsWriter;
    private final CardNewsWriter cardWriter;
    private final com.youthletter.domain.quiz.service.QuizWriter quizWriter;
    @org.springframework.beans.factory.annotation.Value("${ai-service.bundle-mode:false}")
    private boolean bundleMode;
    private static final RowMapper<Job> MAPPER = (rs, row) -> new Job(rs.getLong("id"), rs.getLong("article_id"),
            Kind.valueOf(rs.getString("kind")), rs.getString("status"), rs.getInt("attempts"),
            rs.getString("error_code"), rs.getObject("created_at", java.time.OffsetDateTime.class).withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime(),
            rs.getObject("updated_at", java.time.OffsetDateTime.class).withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDateTime());

    @Transactional
    public Job enqueue(Long articleId, Kind kind) {
        // 동일 기사 접수를 직렬화한다. 작업이 진행 중이면 기존 ID를 반환한다.
        if (jdbc.queryForList("SELECT id FROM news_articles WHERE id=? FOR UPDATE", Long.class, articleId).isEmpty())
            throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        var active = jdbc.query("SELECT * FROM ai_jobs WHERE article_id=? AND kind=? AND status IN ('QUEUED','RUNNING')", MAPPER, articleId, kind.name());
        if (!active.isEmpty()) return active.get(0);
        return jdbc.queryForObject("INSERT INTO ai_jobs(article_id,kind) VALUES (?,?) RETURNING *", MAPPER, articleId, kind.name());
    }

    @Transactional(readOnly = true)
    public Job get(Long id) {
        return jdbc.query("SELECT * FROM ai_jobs WHERE id=?", MAPPER, id).stream().findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.AI_JOB_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<AiProcessResponse.TermDto> knownTerms(String content) {
        return jdbc.query("""
            SELECT name,COALESCE(NULLIF(simple_definition,''),definition) AS explanation FROM financial_terms
            WHERE length(name)>=2 AND length(name)<=40 AND strpos(?,name)>0
            AND COALESCE(NULLIF(simple_definition,''),definition,'')<>''
            ORDER BY length(name) DESC,name LIMIT 12
            """, (rs,row) -> {
                String name = rs.getString("name");
                int start = content.indexOf(name);
                return new AiProcessResponse.TermDto(name,rs.getString("explanation"),start,start+name.length());
            }, content);
    }

    @Transactional
    public Optional<Claim> claim() {
        // 짧은 전역 claim 잠금: 여러 Spring 인스턴스에서도 큐 작업 한 건만 실행한다.
        jdbc.execute("SELECT pg_advisory_xact_lock(20260929)");
        jdbc.update("""
            UPDATE ai_jobs SET status=CASE WHEN attempts>=3 THEN 'FAILED' ELSE 'QUEUED' END,
                lease_token=NULL, lease_until=NULL, available_at=now(), updated_at=now(), error_code='LEASE_EXPIRED'
            WHERE status='RUNNING' AND lease_until < now()
            """);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ai_jobs WHERE status='RUNNING')", Boolean.class)))
            return Optional.empty();
        String queueSql = bundleMode ? """
            SELECT j.* FROM ai_jobs j JOIN news_articles a ON a.id=j.article_id
            WHERE j.status='QUEUED' AND j.available_at<=now()
            ORDER BY CASE WHEN (SELECT count(*) FROM financial_terms t
                WHERE length(t.name) BETWEEN 2 AND 40 AND strpos(a.content,t.name)>0
                AND length(COALESCE(NULLIF(t.simple_definition,''),t.definition,'')) BETWEEN 1 AND 500)>=5
                THEN 0 ELSE 1 END,j.id LIMIT 1 FOR UPDATE OF j SKIP LOCKED
            """ : "SELECT * FROM ai_jobs WHERE status='QUEUED' AND available_at<=now() ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED";
        var queued = jdbc.query(queueSql, MAPPER);
        if (queued.isEmpty()) return Optional.empty();
        String token = UUID.randomUUID().toString();
        Job job = jdbc.queryForObject("""
            UPDATE ai_jobs SET status='RUNNING', attempts=attempts+1, lease_token=?,
                lease_until=now()+interval '10 minutes', updated_at=now() WHERE id=? RETURNING *
            """, MAPPER, token, queued.get(0).id());
        return Optional.of(new Claim(job, token));
    }

    private boolean owns(Claim claim) {
        return !jdbc.queryForList("SELECT id FROM ai_jobs WHERE id=? AND status='RUNNING' AND lease_token=? AND lease_until>now() FOR UPDATE",
                Long.class, claim.job.id(), claim.token).isEmpty();
    }

    @Transactional
    public boolean complete(Claim claim, AiProcessResponse article, AiCardResponse cards, String model) {
        if (!owns(claim)) return false; // 재시작 후 늦게 도착한 이전 작업 결과는 저장하지 않는다.
        if (claim.job.kind() == Kind.ARTICLE) newsWriter.replace(claim.job.articleId(), article, model);
        else cardWriter.save(claim.job.articleId(), cards);
        jdbc.update("UPDATE ai_jobs SET status='SUCCEEDED', error_code=NULL, lease_token=NULL, lease_until=NULL, updated_at=now() WHERE id=?", claim.job.id());
        return true; // 결과 저장과 상태 변경은 같은 트랜잭션이다.
    }

    @Transactional
    public boolean completeQuiz(Claim claim, com.youthletter.domain.news.client.dto.AiQuizResponse quiz) {
        if (!owns(claim)) return false;
        quizWriter.saveIfAbsent(claim.job.articleId(), quiz);
        jdbc.update("UPDATE ai_jobs SET status='SUCCEEDED', error_code=NULL, lease_token=NULL, lease_until=NULL, updated_at=now() WHERE id=?", claim.job.id());
        return true;
    }

    /** 한 번의 AI 추출 결과로 요약·용어·카드·퀴즈와 세 작업 상태를 함께 저장한다. */
    @Transactional
    public boolean completeBundle(Claim claim, AiBundleResponse bundle) {
        if (!owns(claim)) return false;
        if (bundle.article() == null || bundle.article().terms() == null
                || bundle.article().terms().stream().map(AiProcessResponse.TermDto::term).distinct().count() < 5)
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "기사당 용어는 최소 5개가 필요합니다.");
        newsWriter.replace(claim.job.articleId(), bundle.article(), bundle.modelVersion());
        cardWriter.save(claim.job.articleId(), bundle.cards());
        quizWriter.saveIfAbsent(claim.job.articleId(), bundle.quiz());
        jdbc.update("""
            UPDATE ai_jobs j SET status='SUCCEEDED',error_code=NULL,lease_token=NULL,lease_until=NULL,updated_at=now()
            WHERE article_id=? AND (id=? OR (status IN ('QUEUED','FAILED') AND
                id=(SELECT max(k.id) FROM ai_jobs k WHERE k.article_id=j.article_id AND k.kind=j.kind)))
            """, claim.job.articleId(), claim.job.id());
        return true;
    }

    @Transactional
    public void fail(Claim claim, ErrorCode error) {
        fail(claim, error.name(), error == ErrorCode.AI_SERVICE_ERROR || error == ErrorCode.AI_SERVICE_TIMEOUT);
    }

    @Transactional
    public void fail(Claim claim, String errorCode, boolean retryable) {
        if (!owns(claim)) return;
        boolean retry = claim.job.attempts() < 3 && retryable;
        jdbc.update("""
            UPDATE ai_jobs SET status=?, error_code=?, lease_token=NULL, lease_until=NULL,
                available_at=now()+interval '30 seconds', updated_at=now() WHERE id=?
            """, retry ? "QUEUED" : "FAILED", errorCode, claim.job.id());
    }
}
