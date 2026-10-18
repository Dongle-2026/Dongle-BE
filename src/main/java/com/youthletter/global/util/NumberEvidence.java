package com.youthletter.global.util;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 생성문의 숫자가 근거 문장에 있는지 확인한다(AI 서비스 ai.js unsupportedNumbers와 같은 규칙).
 * 만·억·조·천 단위까지 한 덩어리로 읽어 값으로 비교한다.
 * 예) "1천만"="1,000만"은 같은 값, "21만"은 근거 "21만9000"과 값이 달라 불인정, 근거에 없는 "2025년"도 불인정.
 */
public final class NumberEvidence {
    private static final Pattern TOKEN = Pattern.compile("\\d[\\d,.]*(?:[만억조천]+\\d[\\d,.]*)*[만억조천]*(?:%|퍼센트)?");
    private static final Pattern STRUCTURAL_COUNT = Pattern.compile("^\\s?가지");
    private static final Map<Character, BigDecimal> UNIT = Map.of(
            '만', new BigDecimal("1e4"), '억', new BigDecimal("1e8"), '조', new BigDecimal("1e12"));

    private NumberEvidence() {}

    public static List<String> unsupported(String rawText, String rawEvidence) {
        // "14만 9576명"처럼 단위 뒤를 띄어 써도 한 숫자로 읽는다(AI 서비스와 같은 규칙).
        // 같은 줄 안의 띄어쓰기만 합친다(제목·근거 문장은 줄바꿈으로 이어 붙여 서로 합쳐지지 않게).
        String text = rawText.replaceAll("(\\d[만억조천])[ \\t]+(?=\\d)", "$1");
        String evidence = rawEvidence.replaceAll("(\\d[만억조천])[ \\t]+(?=\\d)", "$1");
        // "100분의 30"과 "30%"는 같은 값이므로 근거 쪽 표기를 %로도 인정한다(AI 서비스와 같은 규칙).
        String ev = evidence.replaceAll("100분의\\s?(\\d+(?:\\.\\d+)?)", "$0 $1%");
        Set<String> evidenceValues = new HashSet<>();
        Matcher e = TOKEN.matcher(ev);
        while (e.find()) evidenceValues.add(value(e.group()));
        Set<String> unsupported = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) {
            String token = m.group().replaceAll("[.,]+$", "");
            // "두 가지 이유"처럼 문제 구조를 설명하는 작은 개수는 기사 사실이 아니므로 검사하지 않는다.
            if (token.matches("\\d|10") && STRUCTURAL_COUNT.matcher(text.substring(m.start() + token.length())).find()) continue;
            if (!evidenceValues.contains(value(token))) unsupported.add(token.replace(",", ""));
        }
        // 행사명·금액 등에서 찾은 숫자만으로 달력 연도를 추측하는 것을 차단한다.
        String years = ev.replace(",", "").replaceAll("(\\d{4})\\s+년", "$1년");
        Matcher calendarYear = Pattern.compile("(?<!\\d)(\\d{4})\\s*년").matcher(text);
        while (calendarYear.find()) {
            String year = calendarYear.group(1);
            if (!unsupported.contains(year) && !Pattern.compile("(?<![\\d.])" + year + "년").matcher(years).find()) {
                unsupported.add(year + "년");
            }
        }
        return List.copyOf(unsupported);
    }

    /** "3조5천억" → 3500000000000, "50퍼센트" → 50%. 표기가 달라도 같은 값이면 같은 문자열. */
    static String value(String token) {
        String raw = token.replace(",", "").replaceAll("퍼센트$", "%");
        raw = raw.replaceAll("\\.+$", "");
        // "10.15.3"처럼 점이 두 개 이상인 날짜·버전 표기는 값이 아니라 글자 그대로 비교한다.
        if (raw.matches(".*\\..*\\..*")) return raw;
        boolean percent = raw.endsWith("%");
        if (percent) raw = raw.substring(0, raw.length() - 1);
        BigDecimal total = BigDecimal.ZERO, section = BigDecimal.ZERO;
        StringBuilder digits = new StringBuilder();
        for (char ch : raw.toCharArray()) {
            if (Character.isDigit(ch) || ch == '.') { digits.append(ch); continue; }
            BigDecimal n = number(digits);
            digits.setLength(0);
            if (ch == '천') section = section.add((n == null ? BigDecimal.ONE : n).multiply(BigDecimal.valueOf(1000)));
            else {
                BigDecimal base = section.add(n == null ? BigDecimal.ZERO : n);
                total = total.add((base.signum() == 0 ? BigDecimal.ONE : base).multiply(UNIT.get(ch)));
                section = BigDecimal.ZERO;
            }
        }
        BigDecimal rest = number(digits);
        total = total.add(section).add(rest == null ? BigDecimal.ZERO : rest);
        return total.stripTrailingZeros().toPlainString() + (percent ? "%" : "");
    }

    private static BigDecimal number(CharSequence digits) {
        String s = digits.toString().replaceAll("\\.+$", "");
        return s.isEmpty() || s.equals(".") ? null : new BigDecimal(s.startsWith(".") ? "0" + s : s);
    }
}
