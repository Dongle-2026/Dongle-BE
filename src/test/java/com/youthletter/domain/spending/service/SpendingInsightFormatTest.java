package com.youthletter.domain.spending.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class SpendingInsightFormatTest {
    @Test void formatsWonWithoutDecimals() {
        assertThat(SpendingService.won(new BigDecimal("58250.00"))).isEqualTo("58,250");
        assertThat(SpendingService.won(BigDecimal.ZERO)).isEqualTo("0");
        assertThat(SpendingService.won(new BigDecimal("1234567.5"))).isEqualTo("1,234,568");
    }
}
