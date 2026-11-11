package com.expense.calculator.dto;

import com.expense.calculator.domain.MatchType;
import jakarta.validation.constraints.NotBlank;

/**
 * @param profileId null = shared by every profile
 */
public record RuleRequest(@NotBlank String keyword, @NotBlank String category, MatchType matchType,
                          com.expense.calculator.domain.RuleScope scope, Long profileId, boolean applyToExisting) {
}
