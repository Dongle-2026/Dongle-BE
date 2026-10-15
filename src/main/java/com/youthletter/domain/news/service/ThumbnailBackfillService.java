package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.service.ArticlePageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 썸네일 수집 이전에 저장된 기사의 원문을 다시 열어 og:image를 채운다(관리자 수동 실행).
 * 원문 사이트 부담을 줄이려고 요청 사이에 1.5초 간격을 두고, 한 번에 최대 100건만 처리한다.
 * 원문이 429(요청 과다)를 돌려주면 그 배치를 즉시 멈추고 rateLimited=true를 돌려준다(나중에 다시 실행).
 * 원문 요청 동안 DB 트랜잭션을 열지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailBackfillService {
    public static final int MAX_BATCH = 100;
    static final long DELAY_MS = 1500;
    private final JdbcTemplate jdbc;

    public record Result(int checked, int updated, int notFound, int failed, boolean rateLimited) {}

    /** 예전에 저장된 로고·기본 이미지 썸네일을 비운다(원문 요청 없음). 비운 건수를 돌려준다. */
    public int clearPlaceholders() {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id, thumbnail_url FROM news_articles WHERE thumbnail_url IS NOT NULL");
        int cleared = 0;
        for (Map<String, Object> row : rows) {
            String url = (String) row.get("thumbnail_url");
            if (ArticlePageExtractor.isPlaceholder(url))
                cleared += jdbc.update("UPDATE news_articles SET thumbnail_url=NULL WHERE id=? AND thumbnail_url=?", row.get("id"), url);
        }
        return cleared;
    }

    public Result backfill(int limit) {
        List<Map<String, Object>> targets = jdbc.queryForList(
                "SELECT id, source_url FROM news_articles WHERE thumbnail_url IS NULL AND source_url LIKE 'http%' ORDER BY id DESC LIMIT ?",
                Math.max(1, Math.min(MAX_BATCH, limit)));
        int updated = 0, notFound = 0, failed = 0;
        boolean rateLimited = false;
        for (int i = 0; i < targets.size(); i++) {
            Long id = ((Number) targets.get(i).get("id")).longValue();
            String url = (String) targets.get(i).get("source_url");
            try {
                if (i > 0) Thread.sleep(DELAY_MS);
                String image = ArticlePageExtractor.extract(Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(10_000).get());
                if (image == null) { notFound++; continue; }
                updated += jdbc.update("UPDATE news_articles SET thumbnail_url=? WHERE id=? AND thumbnail_url IS NULL", image, id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (org.jsoup.HttpStatusException e) {
                if (e.getStatusCode() == 429) { rateLimited = true; log.warn("[Thumbnail] 원문 429, 배치 중단: id={}", id); break; }
                log.warn("[Thumbnail] 수집 실패: id={}, status={}", id, e.getStatusCode());
                failed++;
            } catch (Exception e) {
                log.warn("[Thumbnail] 수집 실패: id={}, reason={}", id, e.getClass().getSimpleName());
                failed++;
            }
        }
        return new Result(targets.size(), updated, notFound, failed, rateLimited);
    }

    /**
     * 섹션명이 붙어 저장된 기존 제목을 원문에서 다시 읽어 고친다(값이 다를 때만).
     * afterId보다 큰 기사부터 최대 limit건. 다음 호출에 쓸 lastId를 돌려준다.
     */
    public TitleResult refreshTitles(long afterId, int limit) {
        List<Map<String, Object>> targets = jdbc.queryForList(
                "SELECT id, source_url, title FROM news_articles WHERE id > ? AND source_url LIKE 'http%' ORDER BY id LIMIT ?",
                afterId, Math.max(1, Math.min(MAX_BATCH, limit)));
        int updated = 0, failed = 0;
        boolean rateLimited = false;
        long lastId = afterId;
        for (int i = 0; i < targets.size(); i++) {
            Long id = ((Number) targets.get(i).get("id")).longValue();
            lastId = id;
            try {
                if (i > 0) Thread.sleep(DELAY_MS);
                String title = ArticlePageExtractor.title(Jsoup.connect((String) targets.get(i).get("source_url"))
                        .userAgent("Mozilla/5.0").timeout(10_000).get());
                if (!title.isBlank() && title.length() <= 300 && !title.equals(targets.get(i).get("title")))
                    updated += jdbc.update("UPDATE news_articles SET title=? WHERE id=?", title, id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (org.jsoup.HttpStatusException e) {
                if (e.getStatusCode() == 429) { rateLimited = true; lastId = id - 1; log.warn("[Title] 원문 429, 배치 중단: id={}", id); break; }
                log.warn("[Title] 수집 실패: id={}, status={}", id, e.getStatusCode());
                failed++;
            } catch (Exception e) {
                log.warn("[Title] 수집 실패: id={}, reason={}", id, e.getClass().getSimpleName());
                failed++;
            }
        }
        return new TitleResult(targets.size(), updated, failed, lastId, rateLimited);
    }

    public record TitleResult(int checked, int updated, int failed, long lastId, boolean rateLimited) {}
}
