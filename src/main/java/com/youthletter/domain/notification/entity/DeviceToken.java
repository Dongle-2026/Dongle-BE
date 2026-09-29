package com.youthletter.domain.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Entity
@Table(name = "device_tokens", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "device_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "device_id", nullable = false, length = 100) private String deviceId;
    @Column(nullable = false, length = 2048) private String token;
    @Column(nullable = false, length = 10) private String platform;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;
    public DeviceToken(Long userId, String deviceId, String token, String platform) {
        this.userId = userId; this.deviceId = deviceId; update(token, platform);
    }
    public void update(String token, String platform) {
        this.token = token; this.platform = platform; updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
