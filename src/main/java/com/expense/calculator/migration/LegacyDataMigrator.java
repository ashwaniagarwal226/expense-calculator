package com.expense.calculator.migration;

import com.expense.calculator.bank.MerchantExtractor;
import com.expense.calculator.bank.RemarkExtractor;
import com.expense.calculator.service.ProfileService;
import com.expense.calculator.domain.Category;
import com.expense.calculator.domain.CategorySource;
import com.expense.calculator.domain.EpnxTransaction;
import com.expense.calculator.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One-time upgrade of a database created by the HDFC-only version (idempotent, runs on every start):
 * <ul>
 *   <li>back-fills BANK = HDFC, CATEGORY_SOURCE and MERCHANT on old rows;</li>
 *   <li>old data can hold two different lines with the same REF_NUM (e.g. a reversal); the later ones get
 *       "#2", "#3" ... appended - the same convention the importer uses for repeated refs in one file, so
 *       re-uploading those statements still matches;</li>
 *   <li>drops older unique constraints that do not include PROFILE_ID - uniqueness is now
 *       (PROFILE_ID, BANK, REF_NUM): the same UPI reference legitimately appears in two banks' statements for a
 *       transfer between own accounts, and in two people's statements when they pay each other;</li>
 *   <li>assigns every row without a profile to the default profile ("Me") and extracts UPI notes.</li>
 * </ul>
 */
@Slf4j
@Component
public class LegacyDataMigrator {

    private final TransactionRepository repository;
    private final JdbcTemplate jdbc;
    private final ProfileService profiles;
    private final RemarkExtractor remarks;

    public LegacyDataMigrator(TransactionRepository repository, JdbcTemplate jdbc, ProfileService profiles,
                              RemarkExtractor remarks) {
        this.repository = repository;
        this.jdbc = jdbc;
        this.profiles = profiles;
        this.remarks = remarks;
    }

    @Transactional
    public void migrate() {
        dropLegacyUniqueConstraints();
        Long defaultProfile = profiles.defaultProfile().getId();
        List<EpnxTransaction> legacy = repository.findAll().stream()
                .filter(t -> t.getBank() == null || t.getCategorySource() == null || t.getMerchant() == null
                        || t.getProfileId() == null || t.getRemark() == null)
                .toList();
        legacy.stream()
                .filter(t -> t.getBank() == null)
                .collect(Collectors.groupingBy(EpnxTransaction::getRefNum))
                .values().stream()
                .filter(group -> group.size() > 1)
                .forEach(group -> {
                    group.sort(Comparator.comparing(EpnxTransaction::getTransId));
                    for (int i = 1; i < group.size(); i++) {
                        group.get(i).setRefNum(group.get(i).getRefNum() + "#" + (i + 1));
                    }
                    log.info("Disambiguated {} legacy transactions sharing ref {}", group.size(), group.get(0).getRefNum());
                });
        for (EpnxTransaction t : legacy) {
            if (t.getBank() == null) t.setBank("HDFC");
            if (t.getProfileId() == null) t.setProfileId(defaultProfile);
            if (t.getRemark() == null) t.setRemark(remarks.extract(t.getBank(), t.getTransactionName()));
            if (t.getMerchant() == null) t.setMerchant(MerchantExtractor.extract(t.getTransactionName()));
            if (t.getCategorySource() == null) {
                boolean known = t.getTransactionType() != null && !Category.OTHERS.equals(t.getTransactionType());
                if (!known) t.setTransactionType(Category.OTHERS);
                t.setCategorySource(known ? CategorySource.RULE : CategorySource.NONE);
            }
        }
        if (!legacy.isEmpty()) {
            repository.saveAll(legacy);
            log.info("Migrated {} legacy transactions (profile/bank/merchant/UPI note/category source back-filled)", legacy.size());
        }
    }

    /** Drops unique constraints on EPNX_TRANSACTIONS that predate profiles (do not include PROFILE_ID). */
    private void dropLegacyUniqueConstraints() {
        try {
            List<String> names = jdbc.queryForList("""
                    SELECT tc.CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                    WHERE tc.TABLE_NAME = 'EPNX_TRANSACTIONS' AND tc.CONSTRAINT_TYPE = 'UNIQUE'
                      AND EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                           WHERE k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND k.TABLE_NAME = tc.TABLE_NAME
                             AND k.COLUMN_NAME = 'REF_NUM')
                      AND NOT EXISTS (SELECT 1 FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE k
                           WHERE k.CONSTRAINT_NAME = tc.CONSTRAINT_NAME AND k.TABLE_NAME = tc.TABLE_NAME
                             AND k.COLUMN_NAME = 'PROFILE_ID')
                    """, String.class);
            for (String name : names) {
                jdbc.execute("ALTER TABLE EPNX_TRANSACTIONS DROP CONSTRAINT \"" + name + "\"");
                log.info("Dropped legacy unique constraint {}", name);
            }
        } catch (RuntimeException e) {
            log.warn("Could not inspect/drop legacy unique constraints: {}", e.getMessage());
        }
    }
}
