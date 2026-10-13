package com.youthletter.domain.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContentReportRequest(@NotNull Reason reason, @Size(max = 1000) String description) {
    public enum Reason { INCORRECT_CONTENT, AMBIGUOUS_ANSWER, OTHER }
}
