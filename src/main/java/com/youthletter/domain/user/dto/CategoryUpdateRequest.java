package com.youthletter.domain.user.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CategoryUpdateRequest(
        @NotNull(message = "관심 카테고리 목록은 필수입니다")
        @Size(max = 6, message = "관심 카테고리는 최대 6개까지 선택 가능합니다")
        List<String> categories
) {
}
