package com.youthletter.domain.quiz.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 뉴스 본문 학습 화면.
 * intro: AI 요약(없으면 null). sections: 원문 문단(맨 앞 사진 출처 표기 제외, 소제목은 만들지 않으므로 subtitle=null).
 * quizStatus: READY | GENERATING(AI 생성 중, 잠시 후 다시 조회) | UNAVAILABLE.
 * tags: 기사 카테고리 + 본문 용어(최대 5개).
 */
public record NewsBodyResponse(
        Long newsId, String title, String intro, String source, LocalDateTime publishedAt,
        List<Section> sections, List<Term> terms, String quizStatus, List<Quiz> quizzes, List<String> tags
) {
    public record Section(String id, String subtitle, String text) {}

    /** saved: 로그인 시 내가 저장한 용어인지 */
    public record Term(Long termId, String term, String definition, String category, Boolean saved) {}

    /** 정답·해설은 제출 전에는 보내지 않는다. myResult는 로그인 시에만. */
    public record Quiz(Long id, String question, List<Choice> choices, int honey, MyResult myResult) {}

    public record Choice(String id, String text) {}

    /** revealed: "정답 보기"를 눌러 이 문제 꿀을 포기했는지 */
    public record MyResult(int attempts, boolean solved, int earnedHoney, boolean revealed) {}
}
