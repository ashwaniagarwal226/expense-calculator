package com.expense.calculator.category;

import com.expense.calculator.domain.CategoryRule;
import com.expense.calculator.domain.EpnxTransaction;
import com.expense.calculator.domain.MatchType;
import com.expense.calculator.domain.RuleScope;
import com.expense.calculator.repository.CategoryRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Data-driven replacement for the old if/else chain. Rules come from the database, are compiled
 * once and cached; call {@link #refresh()} after rules change.
 * <p>
 * Evaluation order:
 * <ol>
 *   <li>REMARK-scoped rules against the UPI note you typed ("taxi", "meds") - your note wins;</li>
 *   <li>then higher priority first (USER 10 &gt; AI 5 &gt; SEED 0);</li>
 *   <li>then rules owned by the transaction's profile before shared rules;</li>
 *   <li>then longer keyword first, so "SWIGGY DINEOUT" beats "SWIGGY".</li>
 * </ol>
 * Rules owned by another profile are ignored.
 */
@Slf4j
@Component
public class RuleBasedCategorizer {

    public record Match(String categoryCode, CategoryRule rule) {
    }

    private record CompiledRule(CategoryRule rule, Pattern pattern) {
    }

    private final CategoryRuleRepository ruleRepository;
    private volatile List<CompiledRule> compiled;

    public RuleBasedCategorizer(CategoryRuleRepository ruleRepository) {
        this.ruleRepository = ruleRepository;
    }

    public Optional<Match> categorize(EpnxTransaction txn) {
        return categorize(txn.getTransactionName(), txn.getRemark(), txn.getProfileId());
    }

    public Optional<Match> categorize(String narration, String remark, Long profileId) {
        for (CompiledRule r : rules()) {
            if (!r.rule().appliesTo(profileId)) continue;
            String text = r.rule().effectiveScope() == RuleScope.REMARK ? remark : narration;
            if (text != null && !text.isBlank() && r.pattern().matcher(text).find()) {
                return Optional.of(new Match(r.rule().getCategoryCode(), r.rule()));
            }
        }
        return Optional.empty();
    }

    /** True when {@code rule} on its own would match the transaction (used to apply a new rule to history). */
    public static boolean matches(CategoryRule rule, EpnxTransaction txn) {
        if (!rule.appliesTo(txn.getProfileId())) return false;
        Pattern p = compile(rule);
        String text = rule.effectiveScope() == RuleScope.REMARK ? txn.getRemark() : txn.getTransactionName();
        return p != null && text != null && p.matcher(text).find();
    }

    public void refresh() {
        compiled = null;
    }

    private List<CompiledRule> rules() {
        List<CompiledRule> local = compiled;
        if (local == null) {
            local = ruleRepository.findAll().stream()
                    .sorted(ORDER)
                    .map(r -> new CompiledRule(r, compile(r)))
                    .filter(r -> r.pattern() != null)
                    .toList();
            compiled = local;
            log.info("Loaded {} categorisation rules", local.size());
        }
        return local;
    }

    static final Comparator<CategoryRule> ORDER =
            Comparator.comparing((CategoryRule r) -> r.effectiveScope() == RuleScope.REMARK ? 0 : 1)
                    .thenComparing(CategoryRule::getPriority, Comparator.reverseOrder())
                    .thenComparing(r -> r.getProfileId() == null ? 1 : 0)
                    .thenComparing(r -> r.getKeyword().length(), Comparator.reverseOrder());

    static Pattern compile(CategoryRule rule) {
        try {
            String regex = rule.getMatchType() == MatchType.REGEX
                    ? rule.getKeyword()
                    // whole word: not preceded/followed by a letter or digit
                    : "(?<![A-Za-z0-9])" + Pattern.quote(rule.getKeyword().trim()) + "(?![A-Za-z0-9])";
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        } catch (PatternSyntaxException e) {
            log.warn("Ignoring invalid rule {} '{}': {}", rule.getId(), rule.getKeyword(), e.getDescription());
            return null;
        }
    }
}
