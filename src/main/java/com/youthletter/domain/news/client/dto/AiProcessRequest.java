package com.youthletter.domain.news.client.dto;

public record AiProcessRequest(
        String articleUrl,
        String title,
        String content,
        String category,
        java.util.List<AiProcessResponse.TermDto> terms
) {
    public AiProcessRequest(String articleUrl, String title, String content, String category) {
        this(articleUrl, title, content, category, java.util.List.of());
    }
}
