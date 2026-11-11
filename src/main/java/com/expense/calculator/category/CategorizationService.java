package com.expense.calculator.category;

import com.expense.calculator.ai.AiCategorizer;
import com.expense.calculator.ai.AiUnavailableException;
import com.expense.calculator.config.ExpenseProperties;
import com.expense.calculator.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Categorisation pipeline: keyword rules first (free, instant), then Gemini for whatever is left,
 * then OTHERS. Confident Gemini answers are turned into AI-sourced rules so the next statement with
 * the same merchant is handled by the rule engine.
 */
@Slf4j
@Service
public class CategorizationService {

    public record AiOutcome(int categorized, int remaining, String error) {
    }

    private static final Set<String> TOO_GENERIC_TO_LEARN = Set.of(
            "UPI", "NEFT", "IMPS", "RTGS", "POS", "ATM", "PAYMENT", "TRANSFER", "UNKNOWN", "BANK", "INDIA", "LIMITED",
            "PVT", "LTD", "PRIVATE", "SERVICES", "TECHNOLOGIES", "RETAIL", "STORE", "PAY", "PAYTM", "GOOGLE", "PHONEPE");

    private final RuleBasedCategorizer rules;
    private final AiCategorizer ai;
    private final CategoryService categories;
    private final ExpenseProperties.Ai aiConfig;

    public CategorizationService(RuleBasedCategorizer rules, AiCategorizer ai, CategoryService categories,
                                 ExpenseProperties properties) {
        this.rules = rules;
        this.ai = ai;
        this.categories = categories;
        this.aiConfig = properties.ai();
    }

    public boolean aiEnabled() {
        return ai.isEnabled();
    }

    /** Applies keyword rules. Returns true if a rule matched. Never touches MANUAL categories. */
    public boolean applyRules(EpnxTransaction txn) {
        if (txn.getCategorySource() == CategorySource.MANUAL) return false;
        Optional<RuleBasedCategorizer.Match> match = rules.categorize(txn);
        if (match.isPresent()) {
            txn.setTransactionType(match.get().categoryCode());
            txn.setCategorySource(CategorySource.RULE);
            return true;
        }
        return false;
    }

    /**
     * Sends the given (uncategorised) transactions to Gemini. Identical narrations are sent once.
     * Transactions Gemini cannot place stay OTHERS / NONE. Failures are reported, not thrown, so an
     * upload never fails because the AI is down.
     */
    public AiOutcome applyAi(List<EpnxTransaction> pending) {
        if (pending.isEmpty()) return new AiOutcome(0, 0, null);
        if (!ai.isEnabled()) {
            pending.forEach(this::markUncategorized);
            return new AiOutcome(0, pending.size(), "Gemini is not configured (set GEMINI_API_KEY)");
        }

        Map<String, List<EpnxTransaction>> byNarration = new LinkedHashMap<>();
        pending.forEach(t -> byNarration.computeIfAbsent(t.getTransactionName(), k -> new ArrayList<>()).add(t));
        List<String> narrations = new ArrayList<>(byNarration.keySet());
        List<AiCategorizer.Item> items = new ArrayList<>();
        for (int i = 0; i < narrations.size(); i++) {
            EpnxTransaction sample = byNarration.get(narrations.get(i)).get(0);
            boolean debit = sample.isDebit();
            BigDecimal amount = debit ? sample.getWithdrawAmt()
                    : Optional.ofNullable(sample.getDepositAmt()).orElse(BigDecimal.ZERO);
            items.add(new AiCategorizer.Item(i, narrations.get(i), sample.getRemark(), debit ? "DEBIT" : "CREDIT", amount));
        }

        Map<Integer, AiCategorizer.Answer> answers;
        try {
            answers = ai.categorize(items, categories.categories());
        } catch (AiUnavailableException e) {
            log.warn("AI categorisation failed: {}", e.getMessage());
            pending.forEach(this::markUncategorized);
            return new AiOutcome(0, pending.size(), e.getMessage());
        }

        int done = 0;
        for (int i = 0; i < narrations.size(); i++) {
            AiCategorizer.Answer answer = answers.get(i);
            List<EpnxTransaction> group = byNarration.get(narrations.get(i));
            if (answer == null || Category.OTHERS.equals(answer.category())) {
                group.forEach(this::markUncategorized);
                continue;
            }
            for (EpnxTransaction t : group) {
                t.setTransactionType(answer.category());
                t.setCategorySource(CategorySource.AI);
                if (answer.merchant() != null && !answer.merchant().isBlank()) {
                    t.setMerchant(answer.merchant().trim().toUpperCase(Locale.ROOT));
                }
            }
            done += group.size();
            maybeLearnRule(narrations.get(i), answer, commonProfile(group));
        }
        return new AiOutcome(done, pending.size() - done, null);
    }

    private void markUncategorized(EpnxTransaction t) {
        if (t.getCategorySource() == CategorySource.MANUAL || t.getCategorySource() == CategorySource.AI) return;
        t.setTransactionType(Category.OTHERS);
        t.setCategorySource(CategorySource.NONE);
    }

    /** Learned rules belong to the profile whose transactions taught them (payee names are personal). */
    private static Long commonProfile(List<EpnxTransaction> group) {
        Long first = group.get(0).getProfileId();
        return group.stream().allMatch(t -> Objects.equals(first, t.getProfileId())) ? first : null;
    }

    private void maybeLearnRule(String narration, AiCategorizer.Answer answer, Long profileId) {
        if (!aiConfig.learnRules() || answer.confidence() < aiConfig.minConfidenceToLearn()) return;
        String merchant = answer.merchant() == null ? "" : answer.merchant().trim();
        if (merchant.length() < 4 || TOO_GENERIC_TO_LEARN.contains(merchant.toUpperCase(Locale.ROOT))) return;
        // only learn keywords that really occur as a whole word in the narration
        Pattern whole = Pattern.compile("(?<![A-Za-z0-9])" + Pattern.quote(merchant) + "(?![A-Za-z0-9])",
                Pattern.CASE_INSENSITIVE);
        if (!whole.matcher(narration).find()) return;
        categories.addRule(merchant.toUpperCase(Locale.ROOT), MatchType.WORD, RuleScope.NARRATION, answer.category(),
                        RuleSource.AI, profileId)
                .ifPresent(r -> log.info("Learned rule '{}' -> {} from Gemini", r.getKeyword(), r.getCategoryCode()));
    }
}
