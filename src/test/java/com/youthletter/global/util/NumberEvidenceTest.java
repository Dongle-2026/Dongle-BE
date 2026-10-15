package com.youthletter.global.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 모델 출력에서 발견된 사례 기준. */
class NumberEvidenceTest {
    @Test void doesNotTurnAnEventNameNumberIntoACalendarYear() {
        assertThat(NumberEvidence.unsupported("2026년 22일 시상", "오는 22일 2026 한국광고주대회를 연다."))
                .containsExactly("2026년");
        assertThat(NumberEvidence.unsupported("2026년 시상", "2026 년에 시상한다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("2026 광고주대회", "2026 한국광고주대회")).isEmpty();
        assertThat(NumberEvidence.unsupported("2026년", "12026년")).containsExactly("2026");
    }

    @Test void acceptsNumbersWrittenAsInTheArticle() {
        assertThat(NumberEvidence.unsupported("새 기준선은 519만원", "A값 319만원에 200만원을 더한 약 519만원이 새 기준선이 된다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("정산 금액 3조7064억 원, 10% 증가", "총 정산 금액은 3조7064억 원으로 약 10%가 증가")).isEmpty();
        assertThat(NumberEvidence.unsupported("6월 17일 시행", "개정 국민연금법은 오는 6월17일부터 시행된다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("1,490.6원", "1490.6원에 주간 거래를 마쳤다")).isEmpty();
        assertThat(NumberEvidence.unsupported("당월 보험료의 100% 이상", "당월 보험료의 100분의 100 이상인 경우")).isEmpty();
        assertThat(NumberEvidence.unsupported("50% 이상", "100분의 30 이상")).containsExactly("50%");
    }

    @Test void acceptsSameValueWrittenDifferently() {
        assertThat(NumberEvidence.unsupported("1천만원 지원", "지원금은 1천만원이다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("1,000만원 지원", "지원금은 1천만원이다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("3조5천억원 규모", "총 3조5천억원 규모다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("50% 증가", "50퍼센트 늘었다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("기사가 든 2가지 이유는?", "금리와 환율이 원인이다.")).isEmpty();
        assertThat(NumberEvidence.value("1천만")).isEqualTo(NumberEvidence.value("10000000"));
        assertThat(NumberEvidence.unsupported("14만 9576명", "14만9576명이다.")).isEmpty();
        assertThat(NumberEvidence.unsupported("14만 명", "14만9576명이다.")).containsExactly("14만");
    }

    @Test void stillRejectsDifferentValues() {
        assertThat(NumberEvidence.unsupported("5천만원", "지원금은 1천만원이다.")).containsExactly("5천만");
        assertThat(NumberEvidence.unsupported("50만원", "50% 늘었다.")).containsExactly("50만");
        assertThat(NumberEvidence.unsupported("2개 은행", "금리와 환율이 원인이다.")).containsExactly("2");
        assertThat(NumberEvidence.unsupported("10.15.4 출시", "10.15.3 버전")).containsExactly("10.15.4");
        assertThat(NumberEvidence.unsupported("10.15.3 출시", "10.15.3 버전")).isEmpty();
    }

    @Test void rejectsInventedTruncatedOrEmbeddedNumbers() {
        assertThat(NumberEvidence.unsupported("2025년 6월 17일", "오는 6월17일부터 시행된다.")).containsExactly("2025");
        assertThat(NumberEvidence.unsupported("평균 21만 원 추가", "평균 21만9000 원을 추가 내게 됐다")).containsExactly("21만");
        assertThat(NumberEvidence.unsupported("17년 만에", "2017년 이후")).containsExactly("17");
        assertThat(NumberEvidence.unsupported("3.7%", "연 3.75%까지")).containsExactly("3.7%");
    }
}
