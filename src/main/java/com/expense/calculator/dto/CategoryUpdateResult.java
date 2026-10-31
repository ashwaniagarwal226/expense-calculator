package com.expense.calculator.dto;

public record CategoryUpdateResult(TransactionDto transaction, Long ruleId, int otherTransactionsUpdated) {
}
