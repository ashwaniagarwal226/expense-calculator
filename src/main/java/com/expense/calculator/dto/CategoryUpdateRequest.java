package com.expense.calculator.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * @param createRule  also remember this as a keyword rule (applied to matching past + future transactions)
 * @param ruleKeyword keyword for the rule; defaults to the transaction's merchant (or its UPI note for REMARK scope)
 * @param ruleScope   NARRATION (default, match the merchant) or REMARK (match the UPI note)
 * @param ruleForAllProfiles false (default): the rule only applies to this transaction's profile
 */
public record CategoryUpdateRequest(@NotBlank String category, boolean createRule, String ruleKeyword,
                                    com.expense.calculator.domain.RuleScope ruleScope, boolean ruleForAllProfiles) {
}
