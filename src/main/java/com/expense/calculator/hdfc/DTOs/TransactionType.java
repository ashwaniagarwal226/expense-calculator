package com.expense.calculator.hdfc.DTOs;

public enum TransactionType {
    SWIGGY("SWIGGY"),
    RENT("RENT"),
    PARENTS("PARENTS"),
    INTERNET("INTERNET"),
    MAID("MAID"),
    DINNER("DINNER"),
    TRAVEL("TRAVEL"),
    CAB_SERVICE("CAB_SERVICE"),
    SALARY("SALARY"),
    INTEREST("INTEREST"),
    SHOPPING("SHOPPING"),
    URBANCOMPANY("URBANCOMPANY"),
    SUBSCRIPTIONS("SUBSCRIPTIONS"),
    LIQUOR("LIQUOR");

    private final String transactiontype;

    TransactionType(String transactiontype) {
        this.transactiontype = transactiontype;
    }

    public String getTransactionType() {
        return transactiontype;
    }

    @Override
    public String toString() {
        return this.transactiontype;
    }
}