package com.expense.calculator.domain;

public enum MatchType {
    /** Case-insensitive whole-word match of the keyword anywhere in the narration. */
    WORD,
    /** Case-insensitive Java regular expression. */
    REGEX
}
