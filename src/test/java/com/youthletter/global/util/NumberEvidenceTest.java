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

    @Test void rejectsInventedTruncatedOrEmbeddedNumbers() {
        assertThat(NumberEvidence.unsupported("2025년 6월 17일", "오는 6월17일부터 시행된다.")).containsExactly("2025");
        assertThat(NumberEvidence.unsupported("평균 21만 원 추가", "평균 21만9000 원을 추가 내게 됐다")).containsExactly("21만");
        assertThat(NumberEvidence.unsupported("17년 만에", "2017년 이후")).containsExactly("17");
        assertThat(NumberEvidence.unsupported("3.7%", "연 3.75%까지")).containsExactly("3.7%");
    }
}
