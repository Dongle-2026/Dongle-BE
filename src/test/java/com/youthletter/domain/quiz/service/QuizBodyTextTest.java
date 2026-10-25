package com.youthletter.domain.quiz.service;

import com.youthletter.domain.quiz.dto.NewsBodyResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 본문 화면용 문단·태그 만들기. */
class QuizBodyTextTest {

    @Test void stripsLeadingPhotoCredits() {
        assertThat(QuizService.stripLeadingCaption("사진=연합뉴스 한은 총재가 말했다.")).isEqualTo("한은 총재가 말했다.");
        assertThat(QuizService.stripLeadingCaption("자료=게티이미지뱅크 생성형 AI 사용자가 늘었다.")).isEqualTo("생성형 AI 사용자가 늘었다.");
        assertThat(QuizService.stripLeadingCaption("사진=chat gpt Q. 60대 부부다.")).isEqualTo("Q. 60대 부부다.");
        assertThat(QuizService.stripLeadingCaption("사진=챗GPT 테슬라가 날았다.")).isEqualTo("테슬라가 날았다.");
        assertThat(QuizService.stripLeadingCaption("사진은 기사와 관련 없음. 사진=게티이미지뱅크 한 어머니는 보험금을 남겼다."))
                .isEqualTo("한 어머니는 보험금을 남겼다.");
        assertThat(QuizService.stripLeadingCaption("  사진=뉴스1  그래픽=김기자 실업급여가 늘었다.")).isEqualTo("실업급여가 늘었다.");
    }

    @Test void keepsOrdinaryTextAndCreditsInsideTheBody() {
        assertThat(QuizService.stripLeadingCaption("한은이 금리를 동결했다. 사진=연합뉴스")).isEqualTo("한은이 금리를 동결했다. 사진=연합뉴스");
        assertThat(QuizService.stripLeadingCaption("사진관 매출이 늘었다.")).isEqualTo("사진관 매출이 늘었다.");
        assertThat(QuizService.stripLeadingCaption(null)).isNull();
    }

    @Test void sectionsStartAfterTheCredit() {
        List<NewsBodyResponse.Section> sections = QuizService.sections("사진=연합뉴스 첫 문장이다. 둘째 문장이다.");
        assertThat(sections).isNotEmpty();
        assertThat(sections.get(0).text()).startsWith("첫 문장이다.");
        assertThat(QuizService.sections("사진=연합뉴스")).isEmpty();
    }

    @Test void tagsAreCategoryThenTermsWithoutDuplicatesUpToFive() {
        List<NewsBodyResponse.Term> terms = List.of(term("ETF"), term("금리"), term("ETF"), term("배당"), term("환율"), term("코스피"));
        assertThat(QuizService.tags("투자", terms)).containsExactly("투자", "ETF", "금리", "배당", "환율");
        assertThat(QuizService.tags(null, List.of(term("금리")))).containsExactly("금리");
        assertThat(QuizService.tags("경제", List.of())).containsExactly("경제");
    }

    private static NewsBodyResponse.Term term(String name) {
        return new NewsBodyResponse.Term(1L, name, "정의", "금융", null);
    }
}
