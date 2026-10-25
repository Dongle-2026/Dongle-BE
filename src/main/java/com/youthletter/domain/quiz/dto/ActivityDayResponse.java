package com.youthletter.domain.quiz.dto;

import java.time.LocalDate;

/** 히트맵 하루. level: 0(없음) 1(1~3꿀) 2(4~6) 3(7~9) 4(10+, 하루 최대치) */
public record ActivityDayResponse(LocalDate date, int level, int honey) {
    public static int level(int honey) {
        if (honey <= 0) return 0;
        if (honey <= 3) return 1;
        if (honey <= 6) return 2;
        if (honey <= 9) return 3;
        return 4;
    }
}
