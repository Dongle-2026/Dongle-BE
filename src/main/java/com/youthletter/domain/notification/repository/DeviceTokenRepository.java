package com.youthletter.domain.notification.repository;
import com.youthletter.domain.notification.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {
    Optional<DeviceToken> findByUserIdAndDeviceId(Long userId, String deviceId);
    void deleteByUserIdAndDeviceId(Long userId, String deviceId);
    void deleteByUserId(Long userId);
    void deleteByTokenAndUserIdNot(String token, Long userId);
}
