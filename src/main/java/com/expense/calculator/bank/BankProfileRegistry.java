package com.expense.calculator.bank;

import com.expense.calculator.config.ExpenseProperties;
import com.expense.calculator.config.ExpenseProperties.BankProfile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Bank layouts configured in banks.yml, by code. */
@Component
public class BankProfileRegistry {

    private final Map<String, BankProfile> profiles = new LinkedHashMap<>();

    public BankProfileRegistry(ExpenseProperties properties) {
        properties.banks().forEach(p -> profiles.put(p.code().toUpperCase(Locale.ROOT), p));
        if (profiles.isEmpty()) {
            throw new IllegalStateException("No bank profiles configured (expense.banks in banks.yml)");
        }
    }

    public List<BankProfile> all() {
        return List.copyOf(profiles.values());
    }

    public Optional<BankProfile> find(String code) {
        return code == null ? Optional.empty() : Optional.ofNullable(profiles.get(code.toUpperCase(Locale.ROOT)));
    }
}
