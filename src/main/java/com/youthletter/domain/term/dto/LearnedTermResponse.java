package com.youthletter.domain.term.dto;

import java.time.LocalDateTime;

public record LearnedTermResponse(
        Long termId,
        String name,
        String simpleDefinition,
        Integer difficultyLevel,
        LocalDateTime learnedAt
) {
}
