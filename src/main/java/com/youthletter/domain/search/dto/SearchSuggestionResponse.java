package com.youthletter.domain.search.dto;

/** type: term(금융 용어 사전) | trending(최근 인기 검색어) */
public record SearchSuggestionResponse(String keyword, String type) {
}
