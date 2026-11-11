package com.expense.calculator.bank;

/** The uploaded file could not be read as a bank statement (unknown layout, corrupt file ...). */
public class StatementParseException extends RuntimeException {
    public StatementParseException(String message) {
        super(message);
    }

    public StatementParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
