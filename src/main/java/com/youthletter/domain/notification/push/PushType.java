package com.youthletter.domain.notification.push;

/** 푸시 종류. setting이 있으면 사용자의 알림 설정(notification_settings)을 따른다. */
public enum PushType {
    DAILY_DIGEST, QUIZ_REMINDER, STREAK_ALERT, POLICY_DEADLINE, TEST
}
