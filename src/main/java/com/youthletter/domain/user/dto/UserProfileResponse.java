package com.youthletter.domain.user.dto;

import java.time.LocalDate;
import java.util.List;

public record UserProfileResponse(
        Long id,
        String email,
        String name,
        String nickname,
        LocalDate birthDate,
        String gender,
        String regionCode,
        String occupation,
        String incomeLevel,
        List<String> interests
) {
}
