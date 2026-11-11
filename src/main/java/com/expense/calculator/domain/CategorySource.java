package com.expense.calculator.domain;

/** How a transaction got its category. Higher-trust sources are never overwritten by lower ones. */
public enum CategorySource {
    /** Matched a keyword rule (seeded, user-created or learned from AI). */
    RULE,
    /** Classified by Gemini because no rule matched. */
    AI,
    /** Set by the user from the UI - never overwritten automatically. */
    MANUAL,
    /** Nothing matched and AI was unavailable - category is OTHERS. */
    NONE
}
