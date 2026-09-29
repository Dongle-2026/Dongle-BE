package com.youthletter.domain.policy.entity;

import com.youthletter.domain.user.entity.User;
import com.youthletter.global.util.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "policy_notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyNotification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id", nullable = false)
    private Policy policy;

    @Column(name = "notify_before_days", nullable = false)
    private Integer notifyBeforeDays;

    @Column(name = "is_sent", nullable = false)
    private Boolean isSent;
    @Column(name = "is_read", nullable = false)
    private boolean read;
    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    @Builder
    public PolicyNotification(User user, Policy policy, Integer notifyBeforeDays) {
        this.user = user;
        this.policy = policy;
        this.notifyBeforeDays = notifyBeforeDays != null ? notifyBeforeDays : 7;
        this.isSent = false;
    }

    public void markAsSent() {
        this.isSent = true;
        this.notifiedAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
    public void markAsRead() {
        if (!read) { read = true; readAt = LocalDateTime.now(java.time.ZoneOffset.UTC); }
    }
}
