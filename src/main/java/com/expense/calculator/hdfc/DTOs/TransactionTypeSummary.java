package com.expense.calculator.hdfc.DTOs;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransactionTypeSummary {
    private String transactionType;
    private BigDecimal totalAmount= BigDecimal.ZERO;
}
