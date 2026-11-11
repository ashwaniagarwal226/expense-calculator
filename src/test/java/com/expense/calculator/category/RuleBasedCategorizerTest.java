package com.expense.calculator.category;

import com.expense.calculator.domain.CategoryRule;
import com.expense.calculator.domain.MatchType;
import com.expense.calculator.domain.RuleScope;
import com.expense.calculator.domain.RuleSource;
import com.expense.calculator.repository.CategoryRuleRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RuleBasedCategorizerTest {

    private static CategoryRule rule(String keyword, String category, RuleSource source) {
        return CategoryRule.builder().keyword(keyword).matchType(MatchType.WORD).categoryCode(category)
                .priority(source.defaultPriority()).source(source).build();
    }

    private RuleBasedCategorizer categorizer(CategoryRule... rules) {
        CategoryRuleRepository repo = mock(CategoryRuleRepository.class);
        when(repo.findAll()).thenReturn(List.of(rules));
        return new RuleBasedCategorizer(repo);
    }

    @Test
    void longerKeywordWinsRegardlessOfOrder() {
        // in the old if/else chain SWIGGY was checked first, so DINNER was unreachable
        RuleBasedCategorizer c = categorizer(rule("SWIGGY", "SWIGGY", RuleSource.SEED),
                rule("SWIGGY DINEOUT", "DINNER", RuleSource.SEED));
        assertThat(c.categorize("UPI-SWIGGY DINEOUT-dineout@axis", "", 1L).orElseThrow().categoryCode()).isEqualTo("DINNER");
        assertThat(c.categorize("UPI-SWIGGY-swiggy@icici", "", 1L).orElseThrow().categoryCode()).isEqualTo("SWIGGY");
    }

    @Test
    void wholeWordAndCaseInsensitive() {
        RuleBasedCategorizer c = categorizer(rule("RENT", "RENT", RuleSource.SEED), rule("oasis", "OASIS_BREEZE", RuleSource.SEED));
        assertThat(c.categorize("IMPS-RENT FOR JAN", "", 1L)).isPresent();
        assertThat(c.categorize("PARENTS TRANSFER", "", 1L)).isEmpty(); // RENT inside PARENTS must not match
        assertThat(c.categorize("UPI-OASIS BREEZE ASSOC", "", 1L)).isPresent();
    }

    @Test
    void userRulesBeatSeedRules() {
        RuleBasedCategorizer c = categorizer(rule("AMAZON", "SHOPPING", RuleSource.SEED),
                rule("AMAZON", "GROCERIES", RuleSource.USER));
        assertThat(c.categorize("AMAZON FRESH", "", 1L).orElseThrow().categoryCode()).isEqualTo("GROCERIES");
    }

    @Test
    void regexRules() {
        CategoryRule r = CategoryRule.builder().keyword("^ATW-\\d+").matchType(MatchType.REGEX)
                .categoryCode("CASH_WITHDRAWAL").priority(0).source(RuleSource.USER).build();
        assertThat(categorizer(r).categorize("ATW-512345XXXXXX1234-S1ANBG12-BANGALORE", "", 1L)).isPresent();
    }

    private static CategoryRule remark(String keyword, String category) {
        CategoryRule r = rule(keyword, category, RuleSource.SEED);
        r.setScope(RuleScope.REMARK);
        return r;
    }

    @Test
    void upiNoteBeatsMerchantRules() {
        // paid a pharmacy you have a USER rule for, but typed "taxi" in the UPI note
        RuleBasedCategorizer c = categorizer(rule("GUPTA PHARMACY", "MEDICAL", RuleSource.USER),
                remark("TAXI", "CAB_SERVICE"), remark("MEDS", "MEDICAL"));
        String narration = "UPI-GUPTA PHARMACY-9748895894@AXISBANK-UTIB0002081-259763607747-TAXI";
        assertThat(c.categorize(narration, "TAXI", 1L).orElseThrow().categoryCode()).isEqualTo("CAB_SERVICE");
        assertThat(c.categorize("UPI-RAHUL SINGH-q2@ybl-YESB0YBLUPI-907923059936-MEDS", "MEDS", 1L)
                .orElseThrow().categoryCode()).isEqualTo("MEDICAL");
    }

    @Test
    void remarkRulesDoNotMatchTheMerchantName() {
        RuleBasedCategorizer c = categorizer(remark("FOOD", "DINNER"));
        assertThat(c.categorize("UPI-NATURES FOOD STORE-nfs@ybl-YESB0YBLUPI-907923059936-UPI", "", 1L)).isEmpty();
    }

    @Test
    void profileRulesOnlyApplyToTheirProfile() {
        CategoryRule mine = rule("SHAILAJA", "MAID", RuleSource.USER);
        mine.setProfileId(1L);
        RuleBasedCategorizer c = categorizer(mine, rule("SHAILAJA", "GIFTS_DONATIONS", RuleSource.SEED));
        assertThat(c.categorize("UPI-SHAILAJA-x@ybl", "", 1L).orElseThrow().categoryCode()).isEqualTo("MAID");
        assertThat(c.categorize("UPI-SHAILAJA-x@ybl", "", 2L).orElseThrow().categoryCode()).isEqualTo("GIFTS_DONATIONS");
    }
}
