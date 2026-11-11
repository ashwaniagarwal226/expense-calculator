package com.expense.calculator.bank;

import com.expense.calculator.config.ExpenseProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pulls the note you typed in your UPI app ("taxi", "meds") out of a narration using the bank's
 * {@code remark-pattern}. Boiler-plate notes added by apps/banks are dropped (see {@code remark-ignore}).
 * Returns "" when there is no usable note, never null.
 */
@Slf4j
@Component
public class RemarkExtractor {

    private final Map<String, Pattern> patterns = new HashMap<>();
    private final List<String> ignore;

    public RemarkExtractor(ExpenseProperties properties) {
        for (ExpenseProperties.BankProfile b : properties.banks()) {
            if (b.remarkPattern() == null || b.remarkPattern().isBlank()) continue;
            try {
                patterns.put(b.code().toUpperCase(Locale.ROOT), Pattern.compile(b.remarkPattern(), Pattern.CASE_INSENSITIVE));
            } catch (PatternSyntaxException e) {
                log.warn("Invalid remark-pattern for {}: {}", b.code(), e.getDescription());
            }
        }
        this.ignore = properties.remarkIgnore().stream().map(s -> s.toUpperCase(Locale.ROOT).trim()).toList();
    }

    public String extract(String bank, String narration) {
        if (bank == null || narration == null) return "";
        Pattern p = patterns.get(bank.toUpperCase(Locale.ROOT));
        if (p == null) return "";
        Matcher m = p.matcher(narration.trim());
        if (!m.find()) return "";
        String remark = m.group("remark").trim().toUpperCase(Locale.ROOT);
        if (remark.length() < 2 || remark.matches("[0-9A-Z]*\\d{6,}[0-9A-Z]*")) return ""; // order ids
        for (String noise : ignore) {
            if (remark.equals(noise) || remark.startsWith(noise + " ")) return "";
        }
        return remark;
    }
}
