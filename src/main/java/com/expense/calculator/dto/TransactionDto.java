package com.expense.calculator.dto;

import com.expense.calculator.domain.Category;
import com.expense.calculator.domain.CategorySource;
import com.expense.calculator.domain.EpnxTransaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TransactionDto(
        Long id, Long profileId, String bank, LocalDate date, String narration, String merchant, String remark, String refNum,
        BigDecimal withdrawal, BigDecimal deposit, BigDecimal balance,
        String category, String categoryName, String categoryIcon, CategorySource categorySource) {

    public static TransactionDto of(EpnxTransaction t, Category c) {
        return new TransactionDto(t.getTransId(), t.getProfileId(), t.getBank(), t.getTransactionDate().toLocalDate(),
                t.getTransactionName(), t.getMerchant(), t.getRemark(), t.getRefNum(), t.getWithdrawAmt(), t.getDepositAmt(),
                t.getClosingBalance(), c.getCode(), c.getDisplayName(), c.getIcon(), t.getCategorySource());
    }
}
