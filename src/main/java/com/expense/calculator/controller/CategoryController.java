package com.expense.calculator.controller;

import com.expense.calculator.category.CategoryService;
import com.expense.calculator.domain.Category;
import com.expense.calculator.domain.CategoryRule;
import com.expense.calculator.domain.RuleSource;
import com.expense.calculator.dto.RuleRequest;
import com.expense.calculator.service.ProfileService;
import com.expense.calculator.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("v1")
public class CategoryController {

    private final CategoryService categories;
    private final TransactionService transactions;
    private final ProfileService profiles;

    public CategoryController(CategoryService categories, TransactionService transactions, ProfileService profiles) {
        this.categories = categories;
        this.transactions = transactions;
        this.profiles = profiles;
    }

    @GetMapping("/categories")
    public List<Category> categories() {
        return categories.categories();
    }

    /** Create or update a category (code is the key). */
    @PostMapping("/categories")
    public Category saveCategory(@RequestBody Category category) {
        return categories.saveCategory(category);
    }

    /** {@code profileId} given: shared rules + that profile's own; omitted: every rule. */
    @GetMapping("/rules")
    public List<CategoryRule> rules(@RequestParam(required = false) Long profileId) {
        return categories.rules(profiles.checkOptional(profileId));
    }

    @PostMapping("/rules")
    public ResponseEntity<Map<String, Object>> addRule(@Valid @RequestBody RuleRequest request) {
        return categories.addRule(request.keyword(), request.matchType(), request.scope(),
                        request.category().toUpperCase(), RuleSource.USER, profiles.checkOptional(request.profileId()))
                .map(rule -> {
                    int applied = request.applyToExisting() ? transactions.applyRuleToExisting(rule) : 0;
                    return ResponseEntity.status(HttpStatus.CREATED).body(Map.<String, Object>of("rule", rule, "applied", applied));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "A rule for '" + request.keyword() + "' already exists")));
    }

    @DeleteMapping("/rules/{id}")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        categories.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
