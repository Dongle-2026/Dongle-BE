package com.youthletter.domain.activity.dto;

import java.time.LocalDateTime;

/** 읽기 이력 조회 결과(JPQL). 응답 변환 시 꿀 합계를 붙인다. */
public record ReadHistoryRow(Long articleId, String title, String sourceName, String category,
                             LocalDateTime publishedAt, LocalDateTime lastReadAt, String thumbnailUrl) {
}
