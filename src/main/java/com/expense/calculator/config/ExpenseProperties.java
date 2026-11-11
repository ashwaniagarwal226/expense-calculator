package com.expense.calculator.config;

import com.expense.calculator.domain.CategoryKind;
import com.expense.calculator.domain.MatchType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * All app-specific configuration. Bank profiles live in {@code banks.yml}, the initial
 * categories and keyword rules in {@code category-seed.yml}, AI knobs in application.properties.
 */
@ConfigurationProperties(prefix = "expense")
public record ExpenseProperties(
        @DefaultValue Ai ai,
        @DefaultValue List<BankProfile> banks,
        /* UPI notes that are bank/app boiler-plate, not something the user typed. Compared upper-case. */
        @DefaultValue List<String> remarkIgnore,
        @DefaultValue Seed seed) {

    public record Ai(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("25") int batchSize,
            /* Turn confident AI answers into keyword rules so the same merchant never hits Gemini twice. */
            @DefaultValue("true") boolean learnRules,
            @DefaultValue("0.8") double minConfidenceToLearn,
            @DefaultValue("gemini") String providerName,
            @DefaultValue("") String model) {
    }

    /**
     * Describes the layout of one bank's statement export.
     *
     * @param headers         role -> list of accepted column header spellings. Roles: date, valueDate,
     *                        narration, ref, withdrawal, deposit, amount, drCr, balance.
     * @param dateFormats     java.time patterns tried in order (ISO yyyy-MM-dd is always tried too).
     * @param detectKeywords  text that, if found near the top of the file, identifies this bank.
     * @param remarkPattern   regex with a named group {@code remark} that captures the UPI note from a narration.
     */
    public record BankProfile(
            String code,
            String displayName,
            Map<String, List<String>> headers,
            @DefaultValue List<String> dateFormats,
            @DefaultValue List<String> detectKeywords,
            String remarkPattern) {
    }

    public record Seed(@DefaultValue List<SeedCategory> categories, @DefaultValue List<SeedRule> rules) {
    }

    public record SeedCategory(String code, String displayName, String description, String icon,
                               CategoryKind kind, boolean essential) {
    }

    public record SeedRule(String keyword, String category, @DefaultValue("WORD") MatchType matchType,
                           @DefaultValue("NARRATION") com.expense.calculator.domain.RuleScope scope) {
    }
}
