package com.youthletter.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 커스텀 에러 코드 체계.
 * 프론트엔드에서 에러 코드로 분기 처리할 수 있도록 번호 체계를 정의.
 *
 * 4xxx: 클라이언트 에러
 * 5xxx: 서버 에러
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 인증/인가 (40xx) ──
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, 4001, "인증이 필요합니다"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, 4002, "토큰이 만료되었습니다"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, 4003, "유효하지 않은 토큰입니다"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, 4004, "접근 권한이 없습니다"),

    // ── 사용자 (41xx) ──
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 4100, "사용자를 찾을 수 없습니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, 4101, "이미 가입된 이메일입니다"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, 4102, "이미 사용 중인 닉네임입니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 4103, "이메일 또는 비밀번호가 올바르지 않습니다"),
    OAUTH_ONLY_ACCOUNT(HttpStatus.BAD_REQUEST, 4104, "소셜 로그인 전용 계정입니다. 비밀번호 로그인을 사용할 수 없습니다"),

    // ── 뉴스 (42xx) ──
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, 4200, "뉴스 기사를 찾을 수 없습니다"),

    // ── 용어 (43xx) ──
    TERM_NOT_FOUND(HttpStatus.NOT_FOUND, 4300, "금융 용어를 찾을 수 없습니다"),

    // ── 정책 (44xx) ──
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, 4400, "정책을 찾을 수 없습니다"),
    ALREADY_BOOKMARKED(HttpStatus.CONFLICT, 4401, "이미 즐겨찾기에 추가된 정책입니다"),

    // ── AI Agent (45xx) ──
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, 4500, "채팅 세션을 찾을 수 없습니다"),

    // ── 서버 에러 (50xx) ──
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 5000, "서버 내부 오류가 발생했습니다"),
    AI_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, 5001, "AI 서비스에 연결할 수 없습니다"),
    AI_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, 5002, "AI 서비스 응답 시간이 초과되었습니다"),
    CRAWLING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 5003, "뉴스 크롤링 중 오류가 발생했습니다");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
