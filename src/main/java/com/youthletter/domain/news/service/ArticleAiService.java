package com.youthletter.domain.news.service;

import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ArticleAiService {
    private final AiServiceClient client;
    private final NewsAiWriter writer;
    private final NewsArticleRepository articles;
    @Value("${ai-service.model-version:qwen3:8b}")
    private String model;
    public void process(Long articleId) {
        var article = articles.findById(articleId).orElseThrow(() -> new BusinessException(ErrorCode.ARTICLE_NOT_FOUND));
        var result = client.process(new AiProcessRequest(article.getSourceUrl(), article.getTitle(), article.getContent(), article.getCategory()));
        writer.replace(articleId, result, client.isStubMode() ? "stub" : model);
    }
}
