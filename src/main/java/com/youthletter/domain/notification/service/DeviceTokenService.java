package com.youthletter.domain.notification.service;
import com.youthletter.domain.notification.entity.DeviceToken;
import com.youthletter.domain.notification.repository.DeviceTokenRepository;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.domain.user.entity.User;
import com.youthletter.global.exception.*;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@RequiredArgsConstructor
public class DeviceTokenService {
    private final DeviceTokenRepository repository;
    private final EntityManager entityManager;
    @Transactional
    public void register(Long userId, String deviceId, String token, String platform) {
        if (entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE) == null)
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        var device = repository.findByUserIdAndDeviceId(userId, deviceId)
                .orElseGet(() -> new DeviceToken(userId, deviceId, token, platform));
        device.update(token, platform);
        repository.save(device);
    }
    @Transactional
    public void remove(Long userId, String deviceId) { repository.deleteByUserIdAndDeviceId(userId, deviceId); }
}
