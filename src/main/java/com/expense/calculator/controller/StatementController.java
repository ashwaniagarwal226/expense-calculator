package com.expense.calculator.controller;

import com.expense.calculator.bank.BankProfileRegistry;
import com.expense.calculator.dto.BankDto;
import com.expense.calculator.dto.ImportResult;
import com.expense.calculator.dto.LegacyMonthSummary;
import com.expense.calculator.service.InsightsService;
import com.expense.calculator.service.StatementImportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("v1")
public class StatementController {

    private final StatementImportService importService;
    private final BankProfileRegistry banks;
    private final InsightsService insights;

    public StatementController(StatementImportService importService, BankProfileRegistry banks, InsightsService insights) {
        this.importService = importService;
        this.banks = banks;
        this.insights = insights;
    }

    @GetMapping("/banks")
    public List<BankDto> banks() {
        return banks.all().stream().map(b -> new BankDto(b.code(), b.displayName())).toList();
    }

    /**
     * Upload an xls/xlsx/csv statement. {@code bank} = HDFC, ICICI ... or AUTO (default) to detect it.
     * {@code profileId} = whose statement it is (default profile when omitted).
     */
    @PostMapping("/statements/upload")
    public ResponseEntity<ImportResult> upload(@RequestParam("file") MultipartFile file,
                                               @RequestParam(value = "bank", defaultValue = StatementImportService.AUTO) String bank,
                                               @RequestParam(value = "profileId", required = false) Long profileId)
            throws IOException {
        return ResponseEntity.ok(importService.importStatement(file, bank, profileId));
    }

    // ------------------------------------------------ original endpoints, kept for backwards compatibility

    @PostMapping("/hdfc/transactionupload")
    public ResponseEntity<ImportResult> legacyUpload(@RequestParam("file") MultipartFile file) throws IOException {
        return ResponseEntity.ok(importService.importStatement(file, "HDFC", null));
    }

    @GetMapping("/hdfc/graphdata")
    public List<LegacyMonthSummary> legacyGraphData() {
        return insights.legacySummary();
    }
}
