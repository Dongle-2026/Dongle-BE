package com.youthletter.domain.news.client.dto;

import java.math.BigDecimal;
import java.util.Map;

public record AiInsightRequest(String month, BigDecimal total, BigDecimal previousTotal,
                               Map<String, BigDecimal> categoryTotals) {}
