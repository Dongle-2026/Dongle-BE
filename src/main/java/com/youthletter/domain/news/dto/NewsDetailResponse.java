package com.youthletter.domain.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "뉴스 상세 응답")
@Builder
public record NewsDetailResponse(
        @Schema(description = "기사 ID") Long id,
        @Schema(description = "제목") String title,
        @Schema(description = "원문") String content,
        @Schema(description = "출처명") String sourceName,
        @Schema(description = "원문 URL") String sourceUrl,
        @Schema(description = "카테고리") String category,
        @Schema(description = "발행일") LocalDateTime publishedAt,
        @Schema(description = "AI 3줄 요약") String summary,
        @Schema(description = "요약 모델 버전") String modelVersion,
        @Schema(description = "본문 내 용어 하이라이트 목록") List<TermHighlightDto> termHighlights
) {
    @Schema(description = "용어 하이라이트 정보")
    @Builder
    public record TermHighlightDto(
            @Schema(description = "용어 ID") Long termId,
            @Schema(description = "용어명") String termName,
            @Schema(description = "쉬운 설명") String simpleDefinition,
            @Schema(description = "본문 시작 위치") Integer positionStart,
            @Schema(description = "본문 끝 위치") Integer positionEnd
    ) {}
}
