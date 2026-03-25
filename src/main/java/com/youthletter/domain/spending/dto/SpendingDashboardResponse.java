package com.youthletter.domain.spending.dto;

import com.youthletter.domain.spending.entity.SpendingSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "소비 대시보드 응답")
@Builder
public record SpendingDashboardResponse(
        @Schema(description = "조회 월 (2026-03)") String month,
        @Schema(description = "카테고리별 합계") Map<String, BigDecimal> categoryTotals,
        @Schema(description = "총 지출액") BigDecimal totalAmount
) {
    public static SpendingDashboardResponse fromSnapshot(SpendingSnapshot snapshot) {
        // categoryTotals JSON 파싱은 실무에서 ObjectMapper 사용
        // 여기서는 간략히 문자열로 전달
        return SpendingDashboardResponse.builder()
                .month(snapshot.getSnapshotMonth())
                .categoryTotals(Map.of())  // TODO: JSON → Map 파싱
                .totalAmount(snapshot.getTotalAmount())
                .build();
    }

    public static SpendingDashboardResponse fromAggregation(String month, List<Object[]> rows) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] row : rows) {
            String category = (String) row[0];
            BigDecimal amount = (BigDecimal) row[1];
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
