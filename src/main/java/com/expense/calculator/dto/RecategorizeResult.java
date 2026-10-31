package com.expense.calculator.dto;

public record RecategorizeResult(int examined, int ruleCategorized, int aiCategorized, int stillUncategorized,
                                 boolean aiEnabled, String aiError) {
}
