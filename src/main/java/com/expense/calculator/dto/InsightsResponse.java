package com.expense.calculator.dto;

import com.expense.calculator.domain.CategoryKind;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything the dashboard needs in one call. Month list is newest first.
 * <ul>
 *   <li>totalSpent  = every withdrawal (same number the old dashboard showed)</li>
 *   <li>invested    = withdrawals in INVESTMENT categories</li>
 *   <li>transfers   = withdrawals in TRANSFER categories (card bills, self transfers)</li>
 *   <li>expenses    = totalSpent - invested - transfers</li>
 *   <li>savingsRate = (income - expenses) / income, null when there is no income</li>
 * </ul>
 */
public record InsightsResponse(List<MonthInsight> months, Overall overall, boolean aiEnabled) {

    public record MonthInsight(
            int year, int month, String label,
            BigDecimal totalSpent, BigDecimal income, BigDecimal invested, BigDecimal transfers, BigDecimal expenses,
            BigDecimal net, Double savingsRate, BigDecimal essential, BigDecimal discretionary,
            BigDecimal avgDailySpend, int transactionCount, int uncategorizedCount, Double spentChangePct,
            List<CategoryBreakdown> categories, List<MerchantSpend> topMerchants,
            List<TransactionDto> largestTransactions, List<BigDecimal> dailySpend, List<BigDecimal> weekdaySpend,
            List<ProfileSpend> byProfile) {
    }

    /** Per-person totals for the month (one entry when a single profile is selected). */
    public record ProfileSpend(Long profileId, String name, String emoji, BigDecimal spent, BigDecimal expenses,
                               BigDecimal income) {
    }

    public record CategoryBreakdown(String code, String name, String icon, CategoryKind kind, boolean essential,
                                    BigDecimal amount, int count, double share, BigDecimal previousAmount,
                                    Double changePct) {
    }

    public record MerchantSpend(String merchant, String category, BigDecimal amount, int count) {
    }

    public record RecurringPayment(String merchant, String category, String categoryName, BigDecimal avgAmount,
                                   int monthsSeen, LocalDate lastDate, BigDecimal estimatedYearly) {
    }

    public record CategoryAverage(String code, String name, String icon, boolean essential, BigDecimal avgMonthly,
                                  BigDecimal total) {
    }

    public record Overall(int monthsCovered, BigDecimal avgMonthlySpend, BigDecimal avgMonthlyExpenses,
                          BigDecimal avgMonthlyIncome, Double avgSavingsRate, List<CategoryAverage> topCategories,
                          List<RecurringPayment> recurring, List<String> banks) {
    }
}
