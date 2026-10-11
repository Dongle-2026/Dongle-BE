package com.youthletter.domain.notification.dto;

import com.youthletter.domain.notification.entity.NotificationSetting;

public record NotificationSettingResponse(boolean dailyDigest, boolean quizReminder, boolean streakAlert) {
    public static NotificationSettingResponse from(NotificationSetting setting) {
        return new NotificationSettingResponse(setting.isDailyDigest(), setting.isQuizReminder(), setting.isStreakAlert());
    }
}
