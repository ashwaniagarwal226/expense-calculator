package com.expense.calculator.dto;

import java.math.BigDecimal;
import java.util.List;

/** Shape of the original /v1/hdfc/graphdata response, kept for older clients. */
public record LegacyMonthSummary(String month, Integer year, BigDecimal totalSpent, List<Item> transSummary) {
    public record Item(String transactionType, BigDecimal totalAmount) {
    }
}
