package com.youthletter.global.exception;

import com.youthletter.global.response.GlobalResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({java.time.DateTimeException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class})
    protected ResponseEntity<GlobalResponse<Void>> handleInvalidInput(Exception e) {
        return ResponseEntity.badRequest().body(GlobalResponse.error(4105, "입력 형식 또는 날짜를 확인해 주세요"));
    }

    /** 업로드 용량 초과(spring.servlet.multipart.max-file-size) */
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    protected ResponseEntity<GlobalResponse<Void>> handleUploadTooLarge(Exception e) {
        ErrorCode code = ErrorCode.PROFILE_IMAGE_TOO_LARGE;
        return ResponseEntity.status(code.getHttpStatus()).body(GlobalResponse.error(code.getCode(), "사진은 5MB 이하만 올릴 수 있습니다"));
    }

    /** multipart가 아닌 요청 등 */
    @ExceptionHandler({org.springframework.web.multipart.MultipartException.class,
            org.springframework.web.HttpMediaTypeNotSupportedException.class})
    protected ResponseEntity<GlobalResponse<Void>> handleBadMultipart(Exception e) {
        return ResponseEntity.badRequest().body(GlobalResponse.error(4105, "multipart/form-data 형식으로 보내 주세요"));
    }

    /**
     * 비즈니스 예외 처리
     */
    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<GlobalResponse<Void>> handleBusinessException(BusinessException e) {
        log.warn("BusinessException: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity
                .status(errorCode.getHttpStatus())
                .body(GlobalResponse.error(errorCode.getCode(), e.getMessage()));
    }

    /**
     * @Valid 검증 실패 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<GlobalResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));
        log.warn("Validation failed: {}", errorMessage);
        return ResponseEntity
                .badRequest()
                .body(GlobalResponse.error(4000, errorMessage));
    }

    /**
     * 정적 리소스 없음 (favicon.ico 등) — ERROR 로그 없이 404 반환
     */
    @ExceptionHandler(NoResourceFoundException.class)
    protected ResponseEntity<GlobalResponse<Void>> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(GlobalResponse.error(404, e.getMessage()));
    }

    /**
     * 접근 권한 없음 (ROLE_ADMIN 필요한 엔드포인트 접근 시)
     */
    @ExceptionHandler(AccessDeniedException.class)
    protected ResponseEntity<GlobalResponse<Void>> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("AccessDenied: {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(GlobalResponse.error(4004, "접근 권한이 없습니다"));
    }

    /**
     * 브라우저가 응답 수신 전 연결을 끊은 경우 (리다이렉트 등) — 정상 동작이므로 무시
     */
    @ExceptionHandler(ClientAbortException.class)
    protected void handleClientAbort(ClientAbortException e) {
        // Broken pipe: 클라이언트가 먼저 연결을 끊은 것 — 로그 불필요
    }

    /**
     * 그 외 예상치 못한 예외
     */
    @ExceptionHandler(Exception.class)
    protected ResponseEntity<GlobalResponse<Void>> handleException(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity
                .internalServerError()
                .body(GlobalResponse.error(5000, "서버 내부 오류가 발생했습니다"));
    }
}
