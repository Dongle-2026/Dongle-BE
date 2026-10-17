package com.youthletter.domain.report.dto;

import java.time.OffsetDateTime;

public record ContentReportResponse(Long id, String status, OffsetDateTime createdAt) {}
