package com.expense.calculator.domain;

public enum RuleSource {
    /** Shipped in category-seed.yml. */
    SEED,
    /** Created by the user (rules page or "remember" on a manual re-categorisation). */
    USER,
    /** Learned automatically from a confident Gemini classification. */
    AI;

    /** Default priority for rules of this source; higher wins before keyword length is considered. */
    public int defaultPriority() {
        return switch (this) {
            case USER -> 10;
            case AI -> 5;
            case SEED -> 0;
        };
    }
}
