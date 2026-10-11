package com.youthletter.domain.search.dto;

/** isNew: 최근 24시간 안에 이 카테고리 기사가 들어왔는지 */
public record CategoryResponse(String id, String name, boolean isNew) {
}
