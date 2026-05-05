package com.youthletter.domain.policy.service;

import com.youthletter.domain.policy.dto.PolicyDetailResponse;
import com.youthletter.domain.policy.dto.PolicyListResponse;
import com.youthletter.domain.policy.entity.Policy;
import com.youthletter.domain.policy.entity.PolicyBookmark;
import com.youthletter.domain.policy.repository.PolicyBookmarkRepository;
import com.youthletter.domain.policy.repository.PolicyQueryRepository;
import com.youthletter.domain.policy.repository.PolicyRepository;
import com.youthletter.domain.user.entity.User;
import com.youthletter.domain.user.repository.UserRepository;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock PolicyRepository policyRepository;
    @Mock PolicyQueryRepository policyQueryRepository;
    @Mock PolicyBookmarkRepository bookmarkRepository;
    @Mock UserRepository userRepository;

    @InjectMocks PolicyService policyService;

    private Policy samplePolicy;
    private User sampleUser;

    @BeforeEach
    void setUp() {
        samplePolicy = Policy.builder()
                .title("청년 월세 지원")
                .description("청년의 주거비 부담 경감을 위한 지원 사업")
                .summary("월 최대 20만원 월세 지원")
                .organization("국토교통부")
                .regionCode(null)
                .ageMin(19)
                .ageMax(34)
                .incomeCriteria("BELOW_MEDIAN")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .applicationUrl("https://www.myhome.go.kr")
                .build();

        sampleUser = User.builder()
                .email("test@test.com")
                .name("테스트")
                .nickname("tester")
                .birthDate(LocalDate.of(2000, 1, 1))
                .regionCode("26")
                .incomeLevel("BELOW_MEDIAN")
                .build();
    }

    @Nested
    @DisplayName("정책 목록 조회 (getPolicyList)")
    class GetPolicyList {

        @Test
        @DisplayName("regionCode 없이 조회하면 활성 정책 전체 반환")
        void noRegionCode_returnsAllActivePolicies() {
            Pageable pageable = PageRequest.of(0, 10);
            given(policyQueryRepository.findActivePolicies(null, pageable))
                    .willReturn(new PageImpl<>(List.of(samplePolicy)));

            Page<PolicyListResponse> result = policyService.getPolicyList(null, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).title()).isEqualTo("청년 월세 지원");
        }

        @Test
        @DisplayName("regionCode 있으면 지역 필터 QueryDSL 호출")
        void withRegionCode_callsQueryRepositoryWithCode() {
            Pageable pageable = PageRequest.of(0, 10);
            given(policyQueryRepository.findActivePolicies("26", pageable))
                    .willReturn(new PageImpl<>(List.of(samplePolicy)));

            Page<PolicyListResponse> result = policyService.getPolicyList("26", pageable);

            assertThat(result.getContent()).hasSize(1);
            verify(policyQueryRepository).findActivePolicies("26", pageable);
        }
    }

    @Nested
    @DisplayName("프로필 기반 추천 (getRecommendedPolicies)")
    class GetRecommendedPolicies {

        @Test
        @DisplayName("사용자 나이/지역/소득 기준으로 맞춤 정책 반환")
        void returnsMatchingPolicies() {
            Pageable pageable = PageRequest.of(0, 10);
            given(userRepository.findById(1L)).willReturn(Optional.of(sampleUser));
            // 사용자 나이: 2026-05-04 기준 26세
            given(policyQueryRepository.findByProfile(anyInt(), eq("26"), eq("BELOW_MEDIAN"), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of(samplePolicy)));

            Page<PolicyListResponse> result = policyService.getRecommendedPolicies(1L, pageable);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("birthDate 없는 사용자 — 기본 나이 25 사용")
        void noBirthDate_usesDefault25() {
            User userWithoutBirth = User.builder()
                    .email("no-birth@test.com")
                    .name("미입력")
                    .nickname("nobirth")
                    .build();
            Pageable pageable = PageRequest.of(0, 10);
            given(userRepository.findById(2L)).willReturn(Optional.of(userWithoutBirth));
            given(policyQueryRepository.findByProfile(eq(25), isNull(), isNull(), eq(pageable)))
                    .willReturn(new PageImpl<>(List.of()));

            Page<PolicyListResponse> result = policyService.getRecommendedPolicies(2L, pageable);

            assertThat(result.getContent()).isEmpty();
            verify(policyQueryRepository).findByProfile(eq(25), isNull(), isNull(), eq(pageable));
        }

        @Test
        @DisplayName("존재하지 않는 userId — USER_NOT_FOUND 예외")
        void userNotFound_throwsException() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> policyService.getRecommendedPolicies(999L, PageRequest.of(0, 10)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("정책 상세 조회 (getPolicyDetail)")
    class GetPolicyDetail {

        @Test
        @DisplayName("비로그인 — isBookmarked false")
        void anonymous_isBookmarkedFalse() {
            given(policyRepository.findById(1L)).willReturn(Optional.of(samplePolicy));

            PolicyDetailResponse result = policyService.getPolicyDetail(1L, null);

            assertThat(result.isBookmarked()).isFalse();
            assertThat(result.title()).isEqualTo("청년 월세 지원");
        }

        @Test
        @DisplayName("로그인 + 즐겨찾기한 정책 — isBookmarked true")
        void loggedIn_bookmarked_isBookmarkedTrue() {
            given(policyRepository.findById(1L)).willReturn(Optional.of(samplePolicy));
            given(bookmarkRepository.existsByUserIdAndPolicyId(10L, 1L)).willReturn(true);

            PolicyDetailResponse result = policyService.getPolicyDetail(1L, 10L);

            assertThat(result.isBookmarked()).isTrue();
        }

        @Test
        @DisplayName("존재하지 않는 policyId — POLICY_NOT_FOUND 예외")
        void notFound_throwsException() {
            given(policyRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> policyService.getPolicyDetail(999L, null))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.POLICY_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("즐겨찾기 토글 (toggleBookmark)")
    class ToggleBookmark {

        @Test
        @DisplayName("즐겨찾기 없으면 추가 — true 반환")
        void notBookmarked_adds_returnsTrue() {
            given(userRepository.findById(10L)).willReturn(Optional.of(sampleUser));
            given(policyRepository.findById(1L)).willReturn(Optional.of(samplePolicy));
            given(bookmarkRepository.findByUserIdAndPolicyId(10L, 1L)).willReturn(Optional.empty());

            boolean result = policyService.toggleBookmark(10L, 1L);

            assertThat(result).isTrue();
            verify(bookmarkRepository).save(any(PolicyBookmark.class));
        }

        @Test
        @DisplayName("즐겨찾기 있으면 해제 — false 반환")
        void alreadyBookmarked_removes_returnsFalse() {
            PolicyBookmark existing = PolicyBookmark.builder()
                    .user(sampleUser)
                    .policy(samplePolicy)
                    .build();
            given(userRepository.findById(10L)).willReturn(Optional.of(sampleUser));
            given(policyRepository.findById(1L)).willReturn(Optional.of(samplePolicy));
            given(bookmarkRepository.findByUserIdAndPolicyId(10L, 1L)).willReturn(Optional.of(existing));

            boolean result = policyService.toggleBookmark(10L, 1L);

            assertThat(result).isFalse();
            verify(bookmarkRepository).delete(existing);
        }

        @Test
        @DisplayName("존재하지 않는 userId — USER_NOT_FOUND 예외")
        void userNotFound_throwsException() {
            given(userRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> policyService.toggleBookmark(999L, 1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("존재하지 않는 policyId — POLICY_NOT_FOUND 예외")
        void policyNotFound_throwsException() {
            given(userRepository.findById(10L)).willReturn(Optional.of(sampleUser));
            given(policyRepository.findById(999L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> policyService.toggleBookmark(10L, 999L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining(ErrorCode.POLICY_NOT_FOUND.getMessage());
        }
    }
}
