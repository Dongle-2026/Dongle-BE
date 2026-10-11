package com.youthletter.domain.quiz.dto;

import java.time.LocalDate;

/** 히트맵 하루. level: 0(없음) 1(1~10꿀) 2(11~25) 3(26~40) 4(41+) */
public record ActivityDayResponse(LocalDate date, int level, int honey) {
    public static int level(int honey) {
        if (honey <= 0) return 0;
        if (honey <= 10) return 1;
        if (honey <= 25) return 2;
        if (honey <= 40) return 3;
        return 4;
    }
}
