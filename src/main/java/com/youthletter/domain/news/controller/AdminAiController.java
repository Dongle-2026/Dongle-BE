package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.client.AiServiceClient;
import com.youthletter.domain.news.service.NewsAiWriter;
import com.youthletter.domain.news.repository.NewsArticleRepository;
import com.youthletter.domain.news.client.dto.AiProcessRequest;
import com.youthletter.global.response.GlobalResponse;
import com.youthletter.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/ai")
@RequiredArgsConstructor
public class AdminAiController {
    private final AiServiceClient client;
    private final com.youthletter.domain.news.service.ArticleAiService service;
    @GetMapping("/health")
    public GlobalResponse<Map<String, Object>> health() { return GlobalResponse.ok(client.health()); }
    @PostMapping("/articles/{articleId}/process")
    public GlobalResponse<Void> process(@PathVariable Long articleId) {
        service.process(articleId);
        return GlobalResponse.ok();
    }
}
