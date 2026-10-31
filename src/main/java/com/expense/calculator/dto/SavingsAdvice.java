package com.expense.calculator.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * @param source GEMINI when produced by the model, HEURISTIC when computed locally (no key / AI error)
 */
public record SavingsAdvice(
        String source, String model, String summary, BigDecimal estimatedMonthlySavings,
        List<Tip> tips, List<String> quickWins, int monthsAnalyzed, BigDecimal avgMonthlyIncome,
        BigDecimal avgMonthlyExpenses, LocalDateTime generatedAt, String notice) {

    /**
     * @param difficulty EASY | MEDIUM | HARD
     */
    public record Tip(String title, String category, String detail, BigDecimal currentMonthlySpend,
                      BigDecimal suggestedMonthlyBudget, BigDecimal potentialMonthlySaving, String difficulty) {
    }
}
