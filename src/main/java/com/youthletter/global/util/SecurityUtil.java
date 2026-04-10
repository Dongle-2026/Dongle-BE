package com.youthletter.global.util;

import com.youthletter.global.auth.jwt.CustomUserPrincipal;
import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * SecurityContext에서 현재 인증 사용자 정보를 꺼내는 정적 유틸.
 * @AuthenticationPrincipal 주입이 어려운 서비스 레이어에서 사용.
 */
public final class SecurityUtil {

    private SecurityUtil() {}

    /**
     * 현재 인증된 사용자 ID 반환.
     * 미인증 상태면 UNAUTHORIZED 예외.
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getUserId();
    }

    /**
     * 현재 인증된 사용자 이메일 반환.
     */
    public static String getCurrentUserEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return principal.getEmail();
    }
}
