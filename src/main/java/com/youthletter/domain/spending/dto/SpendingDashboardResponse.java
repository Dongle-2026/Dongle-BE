package com.youthletter.domain.spending.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youthletter.domain.spending.entity.SpendingSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Schema(description = "소비 대시보드 응답")
@Builder
public record SpendingDashboardResponse(
        @Schema(description = "조회 월 (2026-03)") String month,
        @Schema(description = "카테고리별 합계") Map<String, BigDecimal> categoryTotals,
        @Schema(description = "총 지출액") BigDecimal totalAmount
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static SpendingDashboardResponse fromSnapshot(SpendingSnapshot snapshot) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        try {
            Map<String, Number> raw = MAPPER.readValue(
                    snapshot.getCategoryTotals(),
                    new TypeReference<>() {});
            raw.forEach((k, v) -> totals.put(k, new BigDecimal(v.toString())));
        } catch (Exception e) {
            log.warn("snapshot categoryTotals 파싱 실패: {}", e.getMessage());
        }
        return SpendingDashboardResponse.builder()
                .month(snapshot.getSnapshotMonth())
                .categoryTotals(totals)
                .totalAmount(snapshot.getTotalAmount())
                .build();
    }

    public static SpendingDashboardResponse fromAggregation(String month, List<Object[]> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String category = (String) row[0];
            BigDecimal amount = new BigDecimal(row[1].toString());
            totals.put(category, amount);
            total = total.add(amount);
        }

        return SpendingDashboardResponse.builder()
                .month(month)
                .categoryTotals(totals)
                .totalAmount(total)
                .build();
    }
}
