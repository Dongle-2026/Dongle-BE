package com.youthletter.domain.news.controller;

import com.youthletter.domain.news.service.AiJobService;
import com.youthletter.global.response.GlobalResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/admin/ai/jobs")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AiJobController {
    private final AiJobService service;
    public record AiJobRequest(@NotNull @Positive Long articleId, @NotNull AiJobService.Kind kind) {}
    @PostMapping
    public ResponseEntity<GlobalResponse<AiJobService.Job>> enqueue(@Valid @RequestBody AiJobRequest request) {
        return ResponseEntity.accepted().body(GlobalResponse.accepted(service.enqueue(request.articleId(), request.kind())));
    }
    @GetMapping("/{id}")
    public GlobalResponse<AiJobService.Job> get(@PathVariable Long id) { return GlobalResponse.ok(service.get(id)); }
}
