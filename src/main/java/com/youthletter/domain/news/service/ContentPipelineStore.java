package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.ZoneOffset;

/** 원문과 세 종류의 작업을 한 트랜잭션으로 저장한다. AI 실패에도 원문은 남는다. */
@Service
@RequiredArgsConstructor
public class ContentPipelineStore {
    private final JdbcTemplate jdbc;
    private final AiJobService jobs;

    @Transactional
    public Long save(CrawledArticleDto article) {
        Long id = jdbc.queryForObject("""
            INSERT INTO news_articles(source_url,source_name,title,content,category,published_at,thumbnail_url)
            VALUES (?,?,?,?,?,?,?)
            ON CONFLICT(source_url) DO UPDATE SET source_url=excluded.source_url RETURNING id
            """, Long.class, article.url(), article.sourceName(), article.title(), article.content(), article.category(),
                article.publishedAt() == null ? null : article.publishedAt().atOffset(ZoneOffset.UTC), article.thumbnailUrl());
        // 재실행 시 성공/실패 이력을 보존한다. 무한 재생성이나 검증 실패의 무한 재시도를 막는다.
        for (var kind : AiJobService.Kind.values()) {
            boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM ai_jobs WHERE article_id=? AND kind=?)", Boolean.class, id, kind.name()));
            if (!exists) jobs.enqueue(id, kind);
        }
        return id;
    }

    @Transactional
    public Long start() {
        jdbc.update("UPDATE content_pipeline_runs SET status='INTERRUPTED', finished_at=now() WHERE status='RUNNING'");
        return jdbc.queryForObject("INSERT INTO content_pipeline_runs DEFAULT VALUES RETURNING id", Long.class);
    }

    @Transactional
    public void finish(Long id, int saved, int failed, String status) {
        jdbc.update("UPDATE content_pipeline_runs SET saved_count=?,failed_count=?,status=?,finished_at=now() WHERE id=?",
                saved, failed, status, id);
    }
}
