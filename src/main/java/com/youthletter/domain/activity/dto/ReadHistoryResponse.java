package com.youthletter.domain.activity.dto;

import java.time.LocalDateTime;

/** 최근 읽은 기사. 같은 기사를 여러 날 읽었으면 가장 최근 읽은 시각 한 건으로 묶는다. */
public record ReadHistoryResponse(
        Long articleId,
        String title,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        LocalDateTime lastReadAt
) {
}
