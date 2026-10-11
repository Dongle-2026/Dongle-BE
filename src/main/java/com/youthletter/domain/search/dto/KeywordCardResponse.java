package com.youthletter.domain.search.dto;

/**
 * 검색 탭 키워드 카드. id는 금융 용어 ID, 카드를 누르면 keyword로 뉴스 검색.
 * image는 기사 썸네일 수집 전까지 null.
 */
public record KeywordCardResponse(Long id, String keyword, String category, String categoryName,
                                  long articleCount, String image) {
}
