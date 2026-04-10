package com.youthletter.domain.user.dto;

import java.time.LocalDate;

public record PolicyProfileRequest(
        LocalDate birthDate,
        String regionCode,
        String occupation,
        String incomeLevel
) {
}
