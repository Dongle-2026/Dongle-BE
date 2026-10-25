package com.youthletter.domain.search.dto;

/**
 * 검색 탭 키워드 카드. id는 금융 용어 ID(기본 키워드이고 같은 용어가 없으면 생략), 카드를 누르면 keyword로 뉴스 검색.
 * articleCount: 최근 7일 이 용어가 나온 기사 수(기본 키워드는 0).
 * image: 해당 용어가 나온 최신 기사의 썸네일(없으면 생략).
 */
public record KeywordCardResponse(Long id, String keyword, String category, String categoryName,
                                  long articleCount, String image) {
}
