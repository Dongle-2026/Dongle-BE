package com.youthletter.domain.search.dto;

/** change: up | down | same. 직전 24시간 순위에 없던 검색어는 change=up, isNew=true. */
public record TrendingKeywordResponse(int rank, String keyword, String change, boolean isNew) {
}
