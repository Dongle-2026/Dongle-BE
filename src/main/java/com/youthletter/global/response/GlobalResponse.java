package com.youthletter.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 모든 API 응답의 공통 래퍼.
 * 프론트엔드(안소윤)와 협의한 응답 형식:
 * {
 *   "status": 200,
 *   "message": "ok",
 *   "data": { ... }
 * }
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GlobalResponse<T> {

    private final int status;
    private final String message;
    private final T data;

    // ── 성공 응답 ──

    public static <T> GlobalResponse<T> ok(T data) {
        return new GlobalResponse<>(200, "ok", data);
    }

    public static <T> GlobalResponse<T> ok(String message, T data) {
        return new GlobalResponse<>(200, message, data);
    }

    public static GlobalResponse<Void> ok() {
        return new GlobalResponse<>(200, "ok", null);
    }

    public static <T> GlobalResponse<T> created(T data) {
        return new GlobalResponse<>(201, "created", data);
    }

    // ── 에러 응답 ──
    public static <T> GlobalResponse<T> accepted(T data) {
        return new GlobalResponse<>(202, "accepted", data);
    }

    public static GlobalResponse<Void> error(int status, String message) {
        return new GlobalResponse<>(status, message, null);
    }

    public static <T> GlobalResponse<T> error(int status, String message, T data) {
        return new GlobalResponse<>(status, message, data);
    }
}
