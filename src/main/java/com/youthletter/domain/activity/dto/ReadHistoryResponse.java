package com.youthletter.domain.activity.dto;

import java.time.LocalDateTime;

/**
 * 최근 읽은 기사. 같은 기사를 여러 날 읽었으면 가장 최근 읽은 시각 한 건으로 묶는다.
 * honey: 이 기사에서 받은 꿀 합계(읽기 + 퀴즈).
 */
public record ReadHistoryResponse(
        Long articleId,
        String title,
        String sourceName,
        String category,
        LocalDateTime publishedAt,
        LocalDateTime lastReadAt,
        long honey
) {
    public static ReadHistoryResponse of(ReadHistoryRow row, long honey) {
        return new ReadHistoryResponse(row.articleId(), row.title(), row.sourceName(), row.category(),
                row.publishedAt(), row.lastReadAt(), honey);
    }
}
