package com.expense.calculator.service;

import com.expense.calculator.category.CategorizationService;
import com.expense.calculator.category.CategoryService;
import com.expense.calculator.category.RuleBasedCategorizer;
import com.expense.calculator.domain.*;
import com.expense.calculator.dto.*;
import com.expense.calculator.repository.TransactionRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class TransactionService {

    private final TransactionRepository repository;
    private final CategoryService categories;
    private final CategorizationService categorization;

    public TransactionService(TransactionRepository repository, CategoryService categories,
                              CategorizationService categorization) {
        this.repository = repository;
        this.categories = categories;
        this.categorization = categorization;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> search(Long profileId, Integer year, Integer month, String category, String bank, String q,
                                               boolean uncategorizedOnly, int page, int size) {
        Specification<EpnxTransaction> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (year != null) {
                LocalDate from = month == null ? LocalDate.of(year, 1, 1) : LocalDate.of(year, month, 1);
                LocalDate to = month == null ? from.plusYears(1) : from.plusMonths(1);
                p.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from.atStartOfDay()));
                p.add(cb.lessThan(root.get("transactionDate"), to.atStartOfDay()));
            }
            if (profileId != null) p.add(cb.equal(root.get("profileId"), profileId));
            if (category != null && !category.isBlank()) p.add(cb.equal(root.get("transactionType"), category));
            if (bank != null && !bank.isBlank()) p.add(cb.equal(root.get("bank"), bank));
            if (uncategorizedOnly) p.add(cb.equal(root.get("transactionType"), Category.OTHERS));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().toUpperCase(Locale.ROOT) + "%";
                p.add(cb.or(cb.like(cb.upper(root.get("transactionName")), like),
                        cb.like(cb.upper(root.get("merchant")), like),
                        cb.like(cb.upper(root.get("remark")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Page<EpnxTransaction> result = repository.findAll(spec, PageRequest.of(Math.max(0, page),
                Math.min(Math.max(1, size), 500), Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("transId"))));
        return new PageResponse<>(result.getContent().stream().map(this::toDto).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize());
    }

    /** Manual override from the UI. Optionally remembers it as a USER rule and applies it to history. */
    @Transactional
    public CategoryUpdateResult updateCategory(Long id, CategoryUpdateRequest request) {
        EpnxTransaction txn = repository.findById(id).orElseThrow(() -> new NoSuchElementException("Transaction " + id));
        String code = request.category().trim().toUpperCase(Locale.ROOT);
        if (!categories.exists(code)) throw new IllegalArgumentException("Unknown category " + code);
        txn.setTransactionType(code);
        txn.setCategorySource(CategorySource.MANUAL);
        txn.setUpdatedDt(java.time.LocalDateTime.now());
        repository.save(txn);

        Long ruleId = null;
        int others = 0;
        if (request.createRule()) {
            RuleScope scope = request.ruleScope() == null ? RuleScope.NARRATION : request.ruleScope();
            String fallback = scope == RuleScope.REMARK ? txn.getRemark() : txn.getMerchant();
            String keyword = request.ruleKeyword() == null || request.ruleKeyword().isBlank()
                    ? fallback : request.ruleKeyword().trim();
            if (keyword == null || keyword.isBlank()) {
                throw new IllegalArgumentException("This transaction has no " + (scope == RuleScope.REMARK ? "UPI note" : "merchant") + " to remember");
            }
            CategoryRule rule = categories.addRule(keyword, MatchType.WORD, scope, code, RuleSource.USER,
                    request.ruleForAllProfiles() ? null : txn.getProfileId()).orElse(null);
            if (rule != null) {
                ruleId = rule.getId();
                others = applyRuleToExisting(rule);
            }
        }
        return new CategoryUpdateResult(toDto(txn), ruleId, others);
    }

    /** Re-categorises past non-manual transactions that the given rule matches. */
    @Transactional
    public int applyRuleToExisting(CategoryRule rule) {
        List<EpnxTransaction> changed = repository.findByCategorySourceNot(CategorySource.MANUAL).stream()
                .filter(t -> !rule.getCategoryCode().equals(t.getTransactionType()))
                .filter(t -> RuleBasedCategorizer.matches(rule, t))
                .peek(t -> {
                    t.setTransactionType(rule.getCategoryCode());
                    t.setCategorySource(CategorySource.RULE);
                })
                .toList();
        repository.saveAll(changed);
        return changed.size();
    }

    /**
     * Re-runs the pipeline over existing data: rules over every non-manual transaction, then Gemini over
     * whatever is still OTHERS (when {@code useAi}).
     */
    @Transactional
    public RecategorizeResult recategorize(Long profileId, boolean useAi) {
        List<EpnxTransaction> all = profileId == null
                ? repository.findByCategorySourceNot(CategorySource.MANUAL)
                : repository.findByProfileIdAndCategorySourceNot(profileId, CategorySource.MANUAL);
        int ruleHits = 0;
        List<EpnxTransaction> pending = new ArrayList<>();
        for (EpnxTransaction t : all) {
            if (categorization.applyRules(t)) {
                ruleHits++;
            } else if (t.getCategorySource() != CategorySource.AI) {
                pending.add(t);
            }
        }
        CategorizationService.AiOutcome ai = useAi
                ? categorization.applyAi(pending)
                : new CategorizationService.AiOutcome(0, pending.size(), null);
        if (!useAi) {
            pending.forEach(t -> {
                t.setTransactionType(Category.OTHERS);
                t.setCategorySource(CategorySource.NONE);
            });
        }
        repository.saveAll(all);
        return new RecategorizeResult(all.size(), ruleHits, ai.categorized(), ai.remaining(),
                categorization.aiEnabled(), ai.error());
    }

    private TransactionDto toDto(EpnxTransaction t) {
        return TransactionDto.of(t, categories.get(t.getTransactionType()));
    }
}
