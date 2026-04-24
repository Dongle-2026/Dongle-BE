package com.youthletter.domain.news.client.dto;

import java.util.List;

public record AiProcessResponse(
        String summary,
        List<TermDto> terms,
        List<Float> embedding
) {
    public record TermDto(
            String term,
            String explanation,
            int startIdx,
            int endIdx
    ) {
    }
}