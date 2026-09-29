package com.youthletter.domain.news.service;

import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.domain.cardnews.service.CardNewsValidator;
import com.youthletter.global.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="ai-jobs.enabled", havingValue="true", matchIfMissing=true)
public class AiJobWorker {
    private final AiJobService jobs;
    private final AiServiceClient client;
    private final NewsArticleRepository articles;
    private final CardNewsValidator validator;
    @Value("${ai-service.model-version:qwen3:8b}") private String model;

    @Scheduled(fixedDelayString="${ai-jobs.poll-ms:3000}")
    public void tick() {
        var next = jobs.claim();
        if (next.isEmpty()) return;
        var claim = next.get();
        try {
            // 큐는 실제 AI 전용: 개발용 스텁을 비동기 결과로 게시하지 않는다.
            if (client.isStubMode()) throw new BusinessException(ErrorCode.AI_STUB_DISABLED);
            var article = articles.findById(claim.job().articleId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
            var request = new AiProcessRequest(article.getSourceUrl(), article.getTitle(), article.getContent(), article.getCategory());
            if (claim.job().kind() == AiJobService.Kind.ARTICLE) {
                jobs.complete(claim, client.process(request), null, model);
            } else {
                var result = client.generateCards(request);
                validator.validate(result, article.getContent());
                jobs.complete(claim, null, result, model);
            }
        } catch (BusinessException e) {
            jobs.fail(claim, e.getErrorCode());
        } catch (RuntimeException e) {
            log.warn("AI job failed: id={}, type={}", claim.job().id(), e.getClass().getSimpleName());
            jobs.fail(claim, ErrorCode.INTERNAL_ERROR);
        }
    }
}
