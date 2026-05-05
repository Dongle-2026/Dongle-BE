package com.youthletter.domain.term.dto;

import com.youthletter.domain.term.entity.FinancialTerm;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "용어 상세 응답 (관련 뉴스 포함)")
public record TermDetailResponse(
        Long id,
        String name,
        String path,
        String definition,
        String simpleDefinition,
        Integer difficultyLevel,
        boolean isLearned,
        List<RelatedArticle> relatedArticles
) {
    public record RelatedArticle(
            Long id,
            String title,
            String category,
            String sourceName,
            LocalDateTime publishedAt
    ) {}

    public static TermDetailResponse of(FinancialTerm term, boolean isLearned,
                                        List<RelatedArticle> relatedArticles) {
        return new TermDetailResponse(
                term.getId(),
                term.getName(),
                term.getPath(),
                term.getDefinition(),
                term.getSimpleDefinition(),
                term.getDifficultyLevel(),
                isLearned,
                relatedArticles
        );
    }
}
