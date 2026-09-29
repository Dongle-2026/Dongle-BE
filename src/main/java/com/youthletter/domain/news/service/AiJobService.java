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
    public enum Kind { ARTICLE, CARD }
    public record Job(Long id, Long articleId, Kind kind, String status, int attempts,
                      String errorCode, LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record Claim(Job job, String token) {}
    private final JdbcTemplate jdbc;
    private final NewsAiWriter newsWriter;
    private final CardNewsWriter cardWriter;
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
        var queued = jdbc.query("SELECT * FROM ai_jobs WHERE status='QUEUED' AND available_at<=now() ORDER BY id LIMIT 1 FOR UPDATE SKIP LOCKED", MAPPER);
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
    public void fail(Claim claim, ErrorCode error) {
        if (!owns(claim)) return;
        boolean retry = claim.job.attempts() < 3 && (error == ErrorCode.AI_SERVICE_ERROR || error == ErrorCode.AI_SERVICE_TIMEOUT);
        jdbc.update("""
            UPDATE ai_jobs SET status=?, error_code=?, lease_token=NULL, lease_until=NULL,
                available_at=now()+interval '30 seconds', updated_at=now() WHERE id=?
            """, retry ? "QUEUED" : "FAILED", error.name(), claim.job.id());
    }
}
