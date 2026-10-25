package com.youthletter.domain.search.dto;

/**
 * change: up | down | same. 직전 24시간 순위에 없던 검색어는 change=up, isNew=true.
 * 검색 기록이 모자라 기사 용어로 채운 항목은 change=same, isNew=false.
 */
public record TrendingKeywordResponse(int rank, String keyword, String change, boolean isNew) {
}
