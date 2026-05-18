package com.youthletter.domain.cardnews.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum CardTemplateType {

    T01("표지형", "기사 제목과 한 줄 요약으로 구성되는 첫 번째 카드"),
    T02("핵심 요약형", "전체 기사의 핵심 내용을 1~2문장으로 요약"),
    T03("개념 설명형", "어려운 금융 개념을 쉬운 말로 풀어서 설명"),
    T04("수치 강조형", "금리·물가·증가율 등 핵심 수치와 의미를 설명"),
    T05("비교형", "이전/이후 또는 두 항목을 나란히 비교"),
    T06("원인 설명형", "현상이 발생한 이유와 배경을 설명"),
    T07("영향 설명형", "뉴스가 청년에게 미치는 실질적 영향 설명"),
    T08("맥락 흐름형", "시간 흐름이나 이슈 배경을 단계별로 설명"),
    T09("용어 정리형", "기사 내 핵심 금융 용어를 정의와 함께 정리"),
    T10("마무리형", "전체 요약과 원문 보기·퀴즈 연결 유도");

    private final String label;
    private final String description;
}
