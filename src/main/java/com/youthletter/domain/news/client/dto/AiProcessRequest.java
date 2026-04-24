package com.youthletter.domain.news.client.dto;

public record AiProcessRequest(
        String articleUrl,
        String title,
        String content,
        String category
) {
}