package com.youthletter.domain.notification.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 앱 알림 설정. 기본값은 모두 켜짐. OS 알림 권한과는 별개의 서비스 내 선호 설정. */
@Entity
@Table(name = "notification_settings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationSetting {
    @Id @Column(name = "user_id") private Long userId;
    @Column(name = "daily_digest", nullable = false) private boolean dailyDigest = true;
    @Column(name = "quiz_reminder", nullable = false) private boolean quizReminder = true;
    @Column(name = "streak_alert", nullable = false) private boolean streakAlert = true;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public static NotificationSetting defaults(Long userId) {
        NotificationSetting setting = new NotificationSetting();
        setting.userId = userId;
        setting.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        return setting;
    }

    /** null 필드는 변경하지 않는다. */
    public void apply(Boolean dailyDigest, Boolean quizReminder, Boolean streakAlert) {
        if (dailyDigest != null) this.dailyDigest = dailyDigest;
        if (quizReminder != null) this.quizReminder = quizReminder;
        if (streakAlert != null) this.streakAlert = streakAlert;
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
