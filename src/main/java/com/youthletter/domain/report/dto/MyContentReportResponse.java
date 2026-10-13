package com.youthletter.domain.report.dto;

import java.time.OffsetDateTime;

public record MyContentReportResponse(Long id, Long quizId, Long cardNewsId, String reason,
        String description, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
