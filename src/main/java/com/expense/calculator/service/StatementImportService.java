package com.expense.calculator.service;

import com.expense.calculator.bank.*;
import com.expense.calculator.category.CategorizationService;
import com.expense.calculator.config.ExpenseProperties.BankProfile;
import com.expense.calculator.domain.CategorySource;
import com.expense.calculator.domain.EpnxTransaction;
import com.expense.calculator.dto.ImportResult;
import com.expense.calculator.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Upload pipeline: read file -> detect bank -> parse -> dedupe -> categorise (rules, then Gemini) -> save. */
@Slf4j
@Service
public class StatementImportService {

    public static final String AUTO = "AUTO";
    private static final String CREATED_BY = "API";

    private final StatementReader reader;
    private final StatementParser parser;
    private final BankProfileRegistry banks;
    private final CategorizationService categorization;
    private final TransactionRepository repository;
    private final RemarkExtractor remarks;
    private final ProfileService profiles;

    public StatementImportService(StatementReader reader, StatementParser parser, BankProfileRegistry banks,
                                  CategorizationService categorization, TransactionRepository repository,
                                  RemarkExtractor remarks, ProfileService profiles) {
        this.remarks = remarks;
        this.profiles = profiles;
        this.reader = reader;
        this.parser = parser;
        this.banks = banks;
        this.categorization = categorization;
        this.repository = repository;
    }

    @Transactional
    public ImportResult importStatement(MultipartFile file, String bankCode, Long requestedProfileId) throws IOException {
        Long profileId = profiles.resolveForUpload(requestedProfileId);
        List<List<String>> rows;
        try (InputStream in = file.getInputStream()) {
            rows = reader.read(in, file.getOriginalFilename());
        }

        boolean auto = bankCode == null || bankCode.isBlank() || AUTO.equalsIgnoreCase(bankCode);
        BankProfile profile = auto
                ? parser.detect(rows, banks.all()).orElseThrow(() -> new StatementParseException(
                        "Could not recognise the bank for this statement. Pick the bank explicitly or add its layout to banks.yml."))
                : banks.find(bankCode).orElseThrow(() -> new IllegalArgumentException("Unsupported bank: " + bankCode));

        List<ParsedTransaction> parsed = parser.parse(rows, profile).transactions();
        if (parsed.isEmpty()) {
            throw new StatementParseException("No transactions found in " + file.getOriginalFilename());
        }

        List<EpnxTransaction> incoming = toEntities(parsed, profile.code(), profileId);
        Map<String, EpnxTransaction> existing = repository
                .findByProfileIdAndBankAndRefNumIn(profileId, profile.code(),
                        incoming.stream().map(EpnxTransaction::getRefNum).toList())
                .stream().collect(Collectors.toMap(EpnxTransaction::getRefNum, Function.identity(), (a, b) -> a));

        int ruleHits = 0;
        int updated = 0;
        List<EpnxTransaction> needAi = new ArrayList<>();
        for (EpnxTransaction txn : incoming) {
            EpnxTransaction old = existing.get(txn.getRefNum());
            if (old != null) {
                updated++;
                txn.setTransId(old.getTransId());
                txn.setCreateDt(old.getCreateDt());
                // keep categories the user set or Gemini already worked out
                if (old.getCategorySource() == CategorySource.MANUAL || old.getCategorySource() == CategorySource.AI) {
                    txn.setTransactionType(old.getTransactionType());
                    txn.setCategorySource(old.getCategorySource());
                    if (old.getMerchant() != null) txn.setMerchant(old.getMerchant());
                }
            }
            if (txn.getCategorySource() == CategorySource.MANUAL) continue;
            if (categorization.applyRules(txn)) {
                ruleHits++;
            } else if (txn.getCategorySource() != CategorySource.AI) {
                needAi.add(txn);
            }
        }

        CategorizationService.AiOutcome aiOutcome = categorization.applyAi(needAi);
        repository.saveAll(incoming);

        int inserted = incoming.size() - updated;
        log.info("Imported {} statement {}: {} new, {} updated, {} by rules, {} by AI, {} uncategorised",
                profile.code(), file.getOriginalFilename(), inserted, updated, ruleHits,
                aiOutcome.categorized(), aiOutcome.remaining());
        return new ImportResult(
                "Imported " + incoming.size() + " transactions from " + profile.displayName()
                        + " for " + profiles.get(profileId).getName(),
                file.getOriginalFilename(), "success", profile.code(), auto, parsed.size(), inserted, updated,
                ruleHits, aiOutcome.categorized(), aiOutcome.remaining(), categorization.aiEnabled(),
                aiOutcome.error(), profileId);
    }

    private List<EpnxTransaction> toEntities(List<ParsedTransaction> parsed, String bank, Long profileId) {
        Map<String, Integer> seen = new HashMap<>();
        List<EpnxTransaction> out = new ArrayList<>(parsed.size());
        LocalDateTime now = LocalDateTime.now();
        for (ParsedTransaction p : parsed) {
            String ref = hasUsableRef(p.refNum()) ? p.refNum().trim() : syntheticRef(p);
            // the same ref twice in one file (reversals, split rows) must not collide
            int n = seen.merge(ref, 1, Integer::sum);
            if (n > 1) ref = ref + "#" + n;
            out.add(EpnxTransaction.builder()
                    .profileId(profileId)
                    .bank(bank)
                    .remark(remarks.extract(bank, p.narration()))
                    .transactionName(p.narration())
                    .merchant(MerchantExtractor.extract(p.narration()))
                    .transactionDate(p.date().atStartOfDay())
                    .valDate(p.valueDate().atStartOfDay())
                    .refNum(ref)
                    .withdrawAmt(p.withdrawal())
                    .depositAmt(p.deposit())
                    .closingBalance(p.balance())
                    .createDt(now)
                    .updatedDt(now)
                    .createdBy(CREATED_BY)
                    .build());
        }
        return out;
    }

    private static boolean hasUsableRef(String ref) {
        return ref != null && !ref.isBlank() && !ref.trim().chars().allMatch(c -> c == '0' || c == '-');
    }

    /** Deterministic id for lines without a reference number, so re-uploading the same file updates instead of duplicating. */
    static String syntheticRef(ParsedTransaction p) {
        String key = String.join("|", p.bank(), String.valueOf(p.date()), p.narration(),
                String.valueOf(p.withdrawal()), String.valueOf(p.deposit()), String.valueOf(p.balance()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return "H-" + HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
