package com.youthletter.domain.news.client;

import com.youthletter.global.exception.BusinessException;
import com.youthletter.global.exception.ErrorCode;
import lombok.Getter;

/** 허용 목록으로 정제된 AI 오류만 영속 작업 상태에 전달한다. */
@Getter
public class AiServiceException extends BusinessException {
    private final String upstreamCode;
    private final boolean retryable;

    public AiServiceException(ErrorCode error, String upstreamCode, boolean retryable) {
        super(error);
        this.upstreamCode = upstreamCode;
        this.retryable = retryable;
    }
}
