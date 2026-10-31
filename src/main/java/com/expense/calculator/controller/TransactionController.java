package com.expense.calculator.controller;

import com.expense.calculator.dto.*;
import com.expense.calculator.service.ProfileService;
import com.expense.calculator.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("v1/transactions")
public class TransactionController {

    private final TransactionService service;
    private final ProfileService profiles;

    public TransactionController(TransactionService service, ProfileService profiles) {
        this.service = service;
        this.profiles = profiles;
    }

    /** {@code profileId} omitted = everyone. */
    @GetMapping
    public PageResponse<TransactionDto> search(@RequestParam(required = false) Long profileId,
                                               @RequestParam(required = false) Integer year,
                                               @RequestParam(required = false) Integer month,
                                               @RequestParam(required = false) String category,
                                               @RequestParam(required = false) String bank,
                                               @RequestParam(required = false) String q,
                                               @RequestParam(defaultValue = "false") boolean uncategorized,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "50") int size) {
        return service.search(profiles.checkOptional(profileId), year, month, category, bank, q, uncategorized, page, size);
    }

    @PatchMapping("/{id}/category")
    public CategoryUpdateResult updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryUpdateRequest request) {
        return service.updateCategory(id, request);
    }

    /** Re-apply rules to all non-manual transactions, then (ai=true) send the remaining OTHERS to Gemini. */
    @PostMapping("/recategorize")
    public RecategorizeResult recategorize(@RequestParam(required = false) Long profileId,
                                           @RequestParam(defaultValue = "true") boolean ai) {
        return service.recategorize(profiles.checkOptional(profileId), ai);
    }
}
