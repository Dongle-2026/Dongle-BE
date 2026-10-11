package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.service.ArticleImageExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 썸네일 수집 이전에 저장된 기사의 원문을 다시 열어 og:image를 채운다(관리자 수동 실행).
 * 원문 사이트 부담을 줄이려고 요청 사이에 간격을 두고, 한 번에 최대 100건만 처리한다.
 * 원문 요청 동안 DB 트랜잭션을 열지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ThumbnailBackfillService {
    static final int MAX_BATCH = 100;
    static final long DELAY_MS = 500;
    private final JdbcTemplate jdbc;

    public record Result(int checked, int updated, int notFound, int failed) {}

    public Result backfill(int limit) {
        List<Map<String, Object>> targets = jdbc.queryForList(
                "SELECT id, source_url FROM news_articles WHERE thumbnail_url IS NULL AND source_url LIKE 'http%' ORDER BY id DESC LIMIT ?",
                Math.max(1, Math.min(MAX_BATCH, limit)));
        int updated = 0, notFound = 0, failed = 0;
        for (int i = 0; i < targets.size(); i++) {
            Long id = ((Number) targets.get(i).get("id")).longValue();
            String url = (String) targets.get(i).get("source_url");
            try {
                if (i > 0) Thread.sleep(DELAY_MS);
                String image = ArticleImageExtractor.extract(Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(10_000).get());
                if (image == null) { notFound++; continue; }
                updated += jdbc.update("UPDATE news_articles SET thumbnail_url=? WHERE id=? AND thumbnail_url IS NULL", image, id);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.warn("[Thumbnail] 수집 실패: id={}, reason={}", id, e.getClass().getSimpleName());
                failed++;
            }
        }
        return new Result(targets.size(), updated, notFound, failed);
    }
}
