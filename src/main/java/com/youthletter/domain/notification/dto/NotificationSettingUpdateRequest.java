package com.youthletter.domain.notification.dto;

/** 부분 변경. 보내지 않은(null) 항목은 유지한다. */
public record NotificationSettingUpdateRequest(Boolean dailyDigest, Boolean quizReminder, Boolean streakAlert) {
}
