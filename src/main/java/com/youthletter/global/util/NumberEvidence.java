package com.youthletter.global.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 생성문의 숫자가 근거 문장에 그대로 있는지 확인한다(AI 서비스 ai.js unsupportedNumbers와 같은 규칙).
 * 만·억·조·천 단위까지 한 덩어리로 보고, 근거에 같은 덩어리가 앞뒤 경계와 함께 있어야 한다.
 * 예) "21만"은 근거 "21만9000"에서 잘린 숫자라 불인정, 근거에 없는 "2025년"도 불인정.
 */
public final class NumberEvidence {
    private static final Pattern TOKEN = Pattern.compile("\\d[\\d,.]*(?:[만억조천]\\d[\\d,.]*)*[만억조천]?(?:%|퍼센트)?");

    private NumberEvidence() {}

    public static List<String> unsupported(String text, String evidence) {
        // "100분의 30"과 "30%"는 같은 값이므로 근거 쪽 표기를 %로도 인정한다(AI 서비스와 같은 규칙).
        String ev = evidence.replace(",", "").replaceAll("100분의\\s?(\\d+(?:\\.\\d+)?)", "$0 $1%");
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = TOKEN.matcher(text);
        while (m.find()) tokens.add(m.group().replace(",", ""));
        Set<String> unsupported = new LinkedHashSet<>(tokens.stream().filter(token -> !supported(token, ev)).toList());
        // 행사명·금액 등에서 찾은 숫자만으로 달력 연도를 추측하는 것을 차단한다.
        String years = ev.replaceAll("(\\d{4})\\s+년", "$1년");
        Matcher calendarYear = Pattern.compile("(?<!\\d)(\\d{4})\\s*년").matcher(text);
        while (calendarYear.find()) {
            String year = calendarYear.group(1);
            if (!unsupported.contains(year) && !Pattern.compile("(?<![\\d.])" + year + "년").matcher(years).find()) {
                unsupported.add(year + "년");
            }
        }
        return List.copyOf(unsupported);
    }

    private static boolean supported(String token, String ev) {
        for (int i = ev.indexOf(token); i >= 0; i = ev.indexOf(token, i + 1)) {
            char before = i > 0 ? ev.charAt(i - 1) : ' ';
            int end = i + token.length();
            char after = end < ev.length() ? ev.charAt(end) : ' ';
            if (!(Character.isDigit(before) || before == '.') && !(Character.isDigit(after) || "만억조천".indexOf(after) >= 0)) return true;
        }
        return false;
    }
}
