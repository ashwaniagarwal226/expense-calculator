package com.expense.calculator.bank;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One statement line, bank-independent. Amounts are positive; null when absent. */
public record ParsedTransaction(
        String bank,
        LocalDate date,
        LocalDate valueDate,
        String narration,
        String refNum,
        BigDecimal withdrawal,
        BigDecimal deposit,
        BigDecimal balance) {

    ParsedTransaction withNarration(String newNarration) {
        return new ParsedTransaction(bank, date, valueDate, newNarration, refNum, withdrawal, deposit, balance);
    }
}
