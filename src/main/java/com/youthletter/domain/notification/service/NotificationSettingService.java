package com.youthletter.domain.notification.service;

import com.youthletter.domain.notification.dto.NotificationSettingResponse;
import com.youthletter.domain.notification.dto.NotificationSettingUpdateRequest;
import com.youthletter.domain.notification.entity.NotificationSetting;
import com.youthletter.domain.notification.repository.NotificationSettingRepository;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {
    private final NotificationSettingRepository repository;
    private final UserRepository userRepository;

    /** 저장된 설정이 없으면 기본값(모두 켜짐)을 반환한다. 조회만으로 행을 만들지 않는다. */
    @Transactional(readOnly = true)
    public NotificationSettingResponse get(Long userId) {
        return NotificationSettingResponse.from(
                repository.findById(userId).orElseGet(() -> NotificationSetting.defaults(userId)));
    }

    @Transactional
    public NotificationSettingResponse update(Long userId, NotificationSettingUpdateRequest request) {
        // 같은 사용자의 동시 최초 저장이 PK 충돌하지 않도록 사용자 행을 잠근다.
        userRepository.findLockedById(userId).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        NotificationSetting setting = repository.findById(userId)
                .orElseGet(() -> repository.save(NotificationSetting.defaults(userId)));
        setting.apply(request.dailyDigest(), request.quizReminder(), request.streakAlert());
        return NotificationSettingResponse.from(setting);
    }
}
