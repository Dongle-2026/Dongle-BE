package com.youthletter.domain.term.dto;

import com.youthletter.domain.term.entity.TermBookmark;

import java.time.LocalDateTime;

public record SavedTermResponse(
        Long termId,
        String name,
        String definition,
        String simpleDefinition,
        String path,
        Integer difficultyLevel,
        LocalDateTime savedAt
) {
    public static SavedTermResponse from(TermBookmark bookmark) {
        var term = bookmark.getTerm();
        return new SavedTermResponse(term.getId(), term.getName(), term.getDefinition(),
                term.getSimpleDefinition(), term.getPath(), term.getDifficultyLevel(), bookmark.getCreatedAt());
    }
}
