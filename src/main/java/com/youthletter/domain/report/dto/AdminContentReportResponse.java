package com.youthletter.domain.report.dto;

import java.time.OffsetDateTime;

public record AdminContentReportResponse(Long id, Long userId, Long quizId, Long cardNewsId,
        String reason, String description, ReportStatus status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public enum ReportStatus { OPEN, REVIEWED, DISMISSED }
}
