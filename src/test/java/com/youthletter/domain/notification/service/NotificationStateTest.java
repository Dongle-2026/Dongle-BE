package com.youthletter.domain.notification.service;

import com.youthletter.domain.policy.entity.PolicyNotification;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class NotificationStateTest {
    @Test void readingDoesNotPretendPushWasSent() {
        var notification = PolicyNotification.builder().build();
        notification.markAsRead();
        var firstRead = notification.getReadAt();
        notification.markAsRead();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getIsSent()).isFalse();
        assertThat(notification.getNotifiedAt()).isNull();
        assertThat(notification.getReadAt()).isEqualTo(firstRead);
    }
    @Test void pushDeliveryDoesNotMarkNotificationRead() {
        var notification = PolicyNotification.builder().build();
        notification.markAsSent();
        assertThat(notification.getIsSent()).isTrue();
        assertThat(notification.isRead()).isFalse();
        assertThat(notification.getReadAt()).isNull();
    }
}
