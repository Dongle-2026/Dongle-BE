package com.youthletter.domain.user.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.domain.activity.repository.ReadingHistoryRepository;
import com.youthletter.domain.activity.repository.TermLookupLogRepository;
import com.youthletter.domain.user.dto.*;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.entity.UserProfile;
import com.youthletter.domain.user.repository.OAuthAccountRepository;
import com.youthletter.domain.user.repository.ProfileImageRepository;
import com.youthletter.domain.user.repository.UserProfileRepository;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final ReadingHistoryRepository readingHistoryRepository;
    private final TermLookupLogRepository termLookupLogRepository;
    private final ObjectMapper objectMapper;
    private final ProfileImageRepository profileImageRepository;
    private final OAuthAccountRepository oAuthAccountRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getMyProfile(Long userId) {
        User user = findUser(userId);
        return toProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, ProfileUpdateRequest request) {
        User user = findUser(userId);

        if (request.nickname() != null && !request.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNickname(request.nickname())) {
                throw new BusinessException(ErrorCode.DUPLICATE_NICKNAME);
            }
        }

        user.updateProfile(request.nickname(), null, null, null, null);
        return toProfileResponse(user);
    }

    @Transactional
    public void updateCategories(Long userId, CategoryUpdateRequest request) {
        User user = findUser(userId);
        String interestsJson = toJson(request.categories());
        user.updateProfile(null, null, null, null, interestsJson);
    }

    @Transactional
    public void updatePolicyProfile(Long userId, PolicyProfileRequest request) {
        User user = findUser(userId);
        user.updatePolicyInfo(request.birthDate(), request.regionCode(),
                request.occupation(), request.incomeLevel());

        // UserProfile이 없으면 기본값으로 초기화
        userProfileRepository.findByUserId(userId)
                .orElseGet(() -> userProfileRepository.save(
                        UserProfile.builder().user(user).build()));
    }

    @Transactional(readOnly = true)
    public LearningStatsResponse getLearningStats(Long userId) {
        long readNewsCount = readingHistoryRepository.countByUserId(userId);
        long learnedTermCount = termLookupLogRepository.countDistinctTermByUserId(userId);
        int consecutiveDays = computeConsecutiveLearningDays(userId);
        return new LearningStatsResponse(readNewsCount, learnedTermCount, consecutiveDays);
    }

    private int computeConsecutiveLearningDays(Long userId) {
        List<LocalDate> dates = readingHistoryRepository.findDistinctReadDatesByUserId(userId)
                .stream()
                .map(java.sql.Date::toLocalDate)
                .toList();
        if (dates.isEmpty()) return 0;

        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
        LocalDate expected = today;
        int count = 0;

        for (LocalDate date : dates) {
            if (date.equals(expected)) {
                count++;
                expected = expected.minusDays(1);
            } else if (date.isBefore(expected)) {
                break;
            }
        }
        return count;
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    UserProfileResponse toProfileResponse(User user) {
        String imageUrl = profileImageRepository.findKeyByUserId(user.getId())
                .map(ProfileImageUrls::of)
                .orElse(null);
        String provider = oAuthAccountRepository.findProvidersByUserId(user.getId()).stream()
                .findFirst().map(p -> p.toLowerCase(java.util.Locale.ROOT)).orElse(null);
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getNickname(),
                user.getBirthDate(),
                user.getGender(),
                user.getRegionCode(),
                user.getOccupation(),
                user.getIncomeLevel(),
                parseInterests(user.getInterests()),
                user.getBio(),
                user.getAvatarId(),
                imageUrl,
                provider,
                user.getCreatedAt()
        );
    }

    private List<String> parseInterests(String interests) {
        if (interests == null || interests.isBlank()) return List.of();
        try {
            return objectMapper.readValue(interests, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("interests JSON 파싱 실패: {}", interests);
            return List.of();
        }
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list);
        } catch (Exception e) {
            return "[]";
        }
    }
}
