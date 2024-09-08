package com.expense.calculator.hdfc.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode
public class TransactionSummaryDTO {
    private String month;
    private Integer year;
    List<TransactionTypeSummary> transSummary;
}
