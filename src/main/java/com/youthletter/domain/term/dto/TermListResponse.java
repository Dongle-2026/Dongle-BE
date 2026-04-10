package com.youthletter.domain.term.dto;

import com.youthletter.domain.term.entity.FinancialTerm;

public record TermListResponse(
        Long id,
        String name,
        String path,
        String simpleDefinition,
        Integer difficultyLevel
) {
    public static TermListResponse from(FinancialTerm term) {
        return new TermListResponse(
                term.getId(),
                term.getName(),
                term.getPath(),
                term.getSimpleDefinition(),
                term.getDifficultyLevel()
        );
    }
}
