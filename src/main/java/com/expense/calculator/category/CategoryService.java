package com.expense.calculator.category;

import com.expense.calculator.config.ExpenseProperties;
import com.expense.calculator.domain.*;
import com.expense.calculator.repository.CategoryRepository;
import com.expense.calculator.repository.CategoryRuleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Categories and keyword rules: seeding on start-up, lookups and CRUD. */
@Slf4j
@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CategoryRuleRepository ruleRepository;
    private final RuleBasedCategorizer ruleCategorizer;
    private final ExpenseProperties properties;
    private volatile Map<String, Category> cache;

    public CategoryService(CategoryRepository categoryRepository, CategoryRuleRepository ruleRepository,
                           RuleBasedCategorizer ruleCategorizer, ExpenseProperties properties) {
        this.categoryRepository = categoryRepository;
        this.ruleRepository = ruleRepository;
        this.ruleCategorizer = ruleCategorizer;
        this.properties = properties;
    }

    /** Adds seed categories that are missing; seeds rules only into an empty rule table. */
    @Transactional
    public void seed() {
        Set<String> existing = categoryRepository.findAll().stream().map(Category::getCode).collect(Collectors.toSet());
        List<Category> missing = properties.seed().categories().stream()
                .filter(c -> !existing.contains(c.code()))
                .map(c -> Category.builder().code(c.code()).displayName(c.displayName()).description(c.description())
                        .icon(c.icon()).kind(c.kind()).essential(c.essential()).build())
                .toList();
        categoryRepository.saveAll(missing);
        if (!categoryRepository.existsById(Category.OTHERS)) {
            categoryRepository.save(Category.builder().code(Category.OTHERS).displayName("Others").icon("📦")
                    .kind(CategoryKind.EXPENSE).essential(false).build());
        }

        if (ruleRepository.count() == 0) {
            List<CategoryRule> rules = properties.seed().rules().stream()
                    .map(r -> newRule(r.keyword(), r.matchType(), r.scope(), r.category(), RuleSource.SEED, null))
                    .toList();
            ruleRepository.saveAll(rules);
            log.info("Seeded {} categories and {} rules", missing.size(), rules.size());
        } else if (!missing.isEmpty()) {
            log.info("Added {} new seed categories", missing.size());
        }
        evict();
    }

    // ---------------------------------------------------------------- categories

    public Map<String, Category> categoryMap() {
        Map<String, Category> local = cache;
        if (local == null) {
            local = categoryRepository.findAll().stream()
                    .collect(Collectors.toMap(Category::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
            cache = local;
        }
        return local;
    }

    public List<Category> categories() {
        return categoryMap().values().stream()
                .sorted(Comparator.comparing(Category::getDisplayName, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    /** Never null: unknown codes (e.g. legacy data) render as a generic expense category. */
    public Category get(String code) {
        String key = code == null ? Category.OTHERS : code;
        return categoryMap().getOrDefault(key, Category.builder().code(key).displayName(prettify(key))
                .icon("📦").kind(CategoryKind.EXPENSE).essential(false).build());
    }

    public boolean exists(String code) {
        return code != null && categoryMap().containsKey(code);
    }

    @Transactional
    public Category saveCategory(Category category) {
        category.setCode(normalizeCode(category.getCode()));
        if (category.getKind() == null) category.setKind(CategoryKind.EXPENSE);
        if (category.getDisplayName() == null || category.getDisplayName().isBlank()) {
            category.setDisplayName(prettify(category.getCode()));
        }
        Category saved = categoryRepository.save(category);
        evict();
        return saved;
    }

    public static String normalizeCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("Category code is required");
        return code.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private static String prettify(String code) {
        String s = code.replace('_', ' ').toLowerCase(Locale.ROOT);
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ---------------------------------------------------------------- rules

    /** All rules, or only those that apply to {@code profileId} (shared + that profile's own). */
    public List<CategoryRule> rules(Long profileId) {
        return ruleRepository.findAll().stream()
                .filter(r -> profileId == null || r.appliesTo(profileId))
                .sorted(Comparator.comparing(CategoryRule::getCategoryCode)
                        .thenComparing(CategoryRule::getKeyword, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** Creates a rule unless an identical one already exists. Returns the new rule, or empty. */
    @Transactional
    public Optional<CategoryRule> addRule(String keyword, MatchType matchType, RuleScope scope, String categoryCode,
                                          RuleSource source, Long profileId) {
        if (keyword == null || keyword.isBlank()) throw new IllegalArgumentException("Keyword is required");
        if (!exists(categoryCode)) throw new IllegalArgumentException("Unknown category " + categoryCode);
        MatchType type = matchType == null ? MatchType.WORD : matchType;
        RuleScope sc = scope == null ? RuleScope.NARRATION : scope;
        CategoryRule candidate = newRule(keyword.trim(), type, sc, categoryCode, source, profileId);
        if (type == MatchType.REGEX && RuleBasedCategorizer.compile(candidate) == null) {
            throw new IllegalArgumentException("Invalid regular expression: " + keyword);
        }
        boolean duplicate = ruleRepository.findAll().stream().anyMatch(r ->
                r.getKeyword().equalsIgnoreCase(candidate.getKeyword()) && r.getMatchType() == type
                        && r.effectiveScope() == sc && Objects.equals(r.getProfileId(), profileId));
        if (duplicate) {
            return Optional.empty();
        }
        CategoryRule saved = ruleRepository.save(candidate);
        ruleCategorizer.refresh();
        return Optional.of(saved);
    }

    @Transactional
    public void deleteRulesOf(Long profileId) {
        ruleRepository.deleteByProfileId(profileId);
        ruleCategorizer.refresh();
    }

    @Transactional
    public void deleteRule(Long id) {
        ruleRepository.deleteById(id);
        ruleCategorizer.refresh();
    }

    private static CategoryRule newRule(String keyword, MatchType type, RuleScope scope, String category,
                                        RuleSource source, Long profileId) {
        return CategoryRule.builder().keyword(keyword).matchType(type).scope(scope).categoryCode(category)
                .profileId(profileId).priority(source.defaultPriority()).source(source)
                .createDt(LocalDateTime.now()).build();
    }

    private void evict() {
        cache = null;
        ruleCategorizer.refresh();
    }
}
