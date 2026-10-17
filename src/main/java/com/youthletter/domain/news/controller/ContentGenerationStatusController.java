package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.service.ContentGenerationStatusService;
import com.youthletter.global.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequiredArgsConstructor
public class ContentGenerationStatusController {
    private final ContentGenerationStatusService service;

    @GetMapping("/api/news/{articleId}/generation")
    public GlobalResponse<List<ContentGenerationStatusService.Stage>> get(@PathVariable Long articleId) {
        return GlobalResponse.ok(service.get(articleId));
    }
}
