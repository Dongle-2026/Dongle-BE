package com.youthletter.domain.news.service;

import com.youthletter.domain.crawler.dto.CrawledArticleDto;
import com.youthletter.domain.crawler.service.HankyungCrawlerService;
import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.client.dto.AiProcessResponse;
import com.youthletter.domain.news.entity.News;
import com.youthletter.domain.news.repository.NewsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class NewsProcessingService {

    private final HankyungCrawlerService crawlerService;
    private final AiServiceClient aiServiceClient;
    private final NewsRepository newsRepository;

    /**
     * 6시간마다 자동 실행: 크롤링 -> AI 처리 -> DB 저장
     */
    @Scheduled(cron = "0 0 */6 * * *")
    public void scheduledProcess() {
        log.info("[Pipeline] 뉴스 크롤링 스케줄 시작");
        processAll();
    }

    /**
     * 크롤링 -> AI 처리 -> DB 저장 파이프라인.
     * 수동 트리거(AdminCrawlController)에서도 호출.
     */
    @Transactional
    public int processAll() {
        List<CrawledArticleDto> articles = crawlerService.crawl();
        int savedCount = 0;

        for (CrawledArticleDto article : articles) {
            try {
                if (newsRepository.existsByOriginalUrl(article.url())) {
                    log.debug("[Pipeline] 이미 저장된 기사 스킵: {}", article.url());
                    continue;
                }

                // 1) AI Service 호출 (요약 + NER + 임베딩)
                AiProcessResponse aiResponse = aiServiceClient.process(
                        new AiProcessRequest(
                                article.url(),
                                article.title(),
                                article.content(),
                                article.category()
                        )
                );

                // 2) News 엔티티 저장
                News news = News.builder()
                        .title(article.title())
                        .content(article.content())
                        .summary(aiResponse.summary())
                        .originalUrl(article.url())
                        .source("한국경제")
                        .category(article.category())
                        .publishedAt(article.publishedAt())
                        .build();

                News saved = newsRepository.save(news);

                // 3) 임베딩 저장 (네이티브 쿼리)
                if (aiResponse.embedding() != null && !aiResponse.embedding().isEmpty()) {
                    String embeddingStr = aiResponse.embedding().stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",", "[", "]"));
                    newsRepository.updateEmbedding(saved.getId(), embeddingStr);
                }

                log.info("[Pipeline] 저장 완료: {}", saved.getTitle());
                savedCount++;
            } catch (Exception e) {
                log.error("[Pipeline] 기사 처리 실패: {} - {}", article.url(), e.getMessage());
            }
        }

        log.info("[Pipeline] 파이프라인 완료 — 총 {}건 저장", savedCount);
        return savedCount;
    }
}