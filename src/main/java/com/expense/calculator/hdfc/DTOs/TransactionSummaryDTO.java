package com.expense.calculator.hdfc.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class TransactionSummaryDTO {
    private String month;
    private Integer year;
    private BigDecimal totalSpent=BigDecimal.ZERO;
    List<TransactionTypeSummary> transSummary;
}
