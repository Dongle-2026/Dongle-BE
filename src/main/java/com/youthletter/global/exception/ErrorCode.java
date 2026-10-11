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
    OAUTH_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, 4005, "지원하지 않는 로그인 제공자입니다"),
    INVALID_ID_TOKEN(HttpStatus.UNAUTHORIZED, 4006, "소셜 로그인 토큰을 확인할 수 없습니다"),
    OAUTH_EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, 4007, "이미 가입된 이메일입니다. 기존 로그인 방식으로 로그인해 주세요"),

    // ── 사용자 (41xx) ──
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, 4100, "사용자를 찾을 수 없습니다"),
    DUPLICATE_EMAIL(HttpStatus.CONFLICT, 4101, "이미 가입된 이메일입니다"),
    DUPLICATE_NICKNAME(HttpStatus.CONFLICT, 4102, "이미 사용 중인 닉네임입니다"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, 4103, "이메일 또는 비밀번호가 올바르지 않습니다"),
    OAUTH_ONLY_ACCOUNT(HttpStatus.BAD_REQUEST, 4104, "소셜 로그인 전용 계정입니다. 비밀번호 로그인을 사용할 수 없습니다"),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, 4105, "유효하지 않은 입력값입니다"),
    PROFILE_IMAGE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, 4106, "프로필 사진 용량이 너무 큽니다"),
    UNSUPPORTED_IMAGE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, 4107, "JPEG, PNG, HEIC, WebP 사진만 올릴 수 있습니다"),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, 4108, "사진 파일을 읽을 수 없습니다"),
    PROFILE_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, 4109, "프로필 사진이 없습니다"),

    // ── 뉴스 (42xx) ──
    ARTICLE_NOT_FOUND(HttpStatus.NOT_FOUND, 4200, "뉴스 기사를 찾을 수 없습니다"),
    CARD_NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, 4201, "카드뉴스를 찾을 수 없습니다"),
    CARD_NEWS_ALREADY_EXISTS(HttpStatus.CONFLICT, 4202, "이미 생성된 카드뉴스가 있습니다"),
    QUIZ_NOT_FOUND(HttpStatus.NOT_FOUND, 4203, "퀴즈를 찾을 수 없습니다"),
    QUIZ_REVEAL_NOT_ALLOWED(HttpStatus.CONFLICT, 4204, "문제를 먼저 풀어 본 뒤 정답을 볼 수 있습니다"),

    // ── 용어 (43xx) ──
    TERM_NOT_FOUND(HttpStatus.NOT_FOUND, 4300, "금융 용어를 찾을 수 없습니다"),

    // ── 정책 (44xx) ──
    POLICY_NOT_FOUND(HttpStatus.NOT_FOUND, 4400, "정책을 찾을 수 없습니다"),
    ALREADY_BOOKMARKED(HttpStatus.CONFLICT, 4401, "이미 즐겨찾기에 추가된 정책입니다"),

    // ── 소비 (46xx) ──
    SPENDING_NOT_FOUND(HttpStatus.NOT_FOUND, 4600, "소비 내역을 찾을 수 없습니다"),
    SPENDING_NOT_OWNED(HttpStatus.FORBIDDEN, 4601, "본인의 소비 내역만 수정/삭제할 수 있습니다"),
    BUDGET_NOT_FOUND(HttpStatus.NOT_FOUND, 4602, "예산이 설정되지 않았습니다"),

    // ── 주식 (47xx) ──
    TICKER_NOT_FOUND(HttpStatus.NOT_FOUND, 4700, "존재하지 않는 종목 코드입니다"),
    WATCHLIST_ALREADY_EXISTS(HttpStatus.CONFLICT, 4701, "이미 관심종목에 추가된 종목입니다"),
    WATCHLIST_NOT_FOUND(HttpStatus.NOT_FOUND, 4702, "관심종목에 없는 종목입니다"),

    // ── AI Agent (45xx) ──
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, 4500, "채팅 세션을 찾을 수 없습니다"),
    AI_JOB_NOT_FOUND(HttpStatus.NOT_FOUND, 4501, "AI 작업을 찾을 수 없습니다"),
    AI_STUB_DISABLED(HttpStatus.CONFLICT, 4502, "비동기 AI 작업은 실제 모델 모드에서만 실행할 수 있습니다"),

    // ── 서버 에러 (50xx) ──
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 5000, "서버 내부 오류가 발생했습니다"),
    AI_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, 5001, "AI 서비스에 연결할 수 없습니다"),
    AI_SERVICE_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, 5002, "AI 서비스 응답 시간이 초과되었습니다"),
    CRAWLING_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, 5003, "뉴스 크롤링 중 오류가 발생했습니다"),
    OAUTH_PROVIDER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, 5004, "소셜 로그인 설정이 아직 완료되지 않았습니다"),
    OAUTH_PROVIDER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, 5005, "소셜 로그인 서버에 연결할 수 없습니다");

    private final HttpStatus httpStatus;
    private final int code;
    private final String message;
}
