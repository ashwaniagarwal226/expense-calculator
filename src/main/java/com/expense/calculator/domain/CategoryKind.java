package com.expense.calculator.domain;

/** Drives how a category is counted in insights. */
public enum CategoryKind {
    /** Money spent - counts towards expenses. */
    EXPENSE,
    /** Money received (salary, interest, refunds). */
    INCOME,
    /** Money moved into savings / investments - counted as saved, not spent. */
    INVESTMENT,
    /** Movement between own accounts / card bills - excluded from expenses. */
    TRANSFER
}
