package com.expense.calculator.bank;

import com.expense.calculator.config.ExpenseProperties.BankProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Generic, configuration-driven statement parser. For a given {@link BankProfile} it
 * <ol>
 *   <li>finds the header row (the first row containing date + narration + amount columns),</li>
 *   <li>maps each role to a column index,</li>
 *   <li>reads every following row whose date cell parses; rows without a date but with text in the
 *       narration column are treated as a wrapped narration of the previous transaction,</li>
 *   <li>stops at a separator row of asterisks (HDFC) once transactions have started.</li>
 * </ol>
 * Auto-detection scores every profile against the file and picks the best.
 */
@Slf4j
@Component
public class StatementParser {

    private static final int HEADER_SEARCH_ROWS = 80;
    private static final List<String> ROLES = List.of(
            "date", "valueDate", "narration", "ref", "withdrawal", "deposit", "amount", "drCr", "balance");

    public record Result(BankProfile profile, List<ParsedTransaction> transactions) {
    }

    private record Header(int rowIndex, Map<String, Integer> columns) {
        boolean has(String role) {
            return columns.containsKey(role);
        }
    }

    /** Picks the profile whose headers (and bank-name keywords) match the file best. */
    public Optional<BankProfile> detect(List<List<String>> rows, List<BankProfile> profiles) {
        BankProfile best = null;
        int bestScore = 0;
        String topText = String.join(" ", rows.stream().limit(HEADER_SEARCH_ROWS)
                .map(r -> String.join(" ", r)).toList()).toUpperCase(Locale.ROOT);
        for (BankProfile profile : profiles) {
            Optional<Header> header = findHeader(rows, profile);
            if (header.isEmpty()) {
                continue;
            }
            int score = header.get().columns().size();
            if (profile.detectKeywords().stream().anyMatch(k -> topText.contains(k.toUpperCase(Locale.ROOT)))) {
                score += 10;
            }
            if (score > bestScore) {
                bestScore = score;
                best = profile;
            }
        }
        return Optional.ofNullable(best);
    }

    public Result parse(List<List<String>> rows, BankProfile profile) {
        Header header = findHeader(rows, profile).orElseThrow(() -> new StatementParseException(
                "Could not find the transaction table header for " + profile.displayName()
                        + ". Expected columns like " + profile.headers().get("date") + ", "
                        + profile.headers().get("narration") + ". Is this the right bank?"));
        List<DateTimeFormatter> formats = formatters(profile);
        List<ParsedTransaction> out = new ArrayList<>();

        for (int i = header.rowIndex() + 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (isBlank(row)) {
                continue;
            }
            if (isStarSeparator(row)) {
                if (!out.isEmpty()) break;
                continue;
            }
            LocalDate date = parseDate(cell(row, header, "date"), formats);
            if (date == null) {
                appendWrappedNarration(out, row, header);
                continue;
            }
            ParsedTransaction txn = toTransaction(profile, row, header, date, formats);
            if (txn != null) {
                out.add(txn);
            }
        }
        log.info("Parsed {} transactions from {} statement", out.size(), profile.code());
        return new Result(profile, out);
    }

    private ParsedTransaction toTransaction(BankProfile profile, List<String> row, Header h, LocalDate date,
                                            List<DateTimeFormatter> formats) {
        BigDecimal withdrawal = Amounts.parse(cell(row, h, "withdrawal"));
        BigDecimal deposit = Amounts.parse(cell(row, h, "deposit"));
        if (withdrawal == null && deposit == null && h.has("amount")) {
            String raw = cell(row, h, "amount");
            BigDecimal amount = Amounts.parse(raw);
            if (amount != null) {
                String drCr = (cell(row, h, "drCr") + " " + raw).toUpperCase(Locale.ROOT);
                boolean credit = drCr.contains("CR") && !drCr.contains("DR");
                if (credit) deposit = amount.abs(); else withdrawal = amount.abs();
            }
        }
        if (withdrawal == null && deposit == null) {
            return null; // opening balance rows etc.
        }
        LocalDate valueDate = Optional.ofNullable(parseDate(cell(row, h, "valueDate"), formats)).orElse(date);
        return new ParsedTransaction(profile.code(), date, valueDate, cell(row, h, "narration"),
                cell(row, h, "ref"), withdrawal, deposit, Amounts.parse(cell(row, h, "balance")));
    }

    private void appendWrappedNarration(List<ParsedTransaction> out, List<String> row, Header h) {
        String text = cell(row, h, "narration");
        boolean onlyNarration = row.stream().filter(s -> !s.isBlank()).count() == 1;
        if (!out.isEmpty() && !text.isBlank() && onlyNarration) {
            int last = out.size() - 1;
            out.set(last, out.get(last).withNarration(out.get(last).narration() + " " + text));
        }
    }

    // ---------------------------------------------------------------- header detection

    private Optional<Header> findHeader(List<List<String>> rows, BankProfile profile) {
        for (int i = 0; i < Math.min(rows.size(), HEADER_SEARCH_ROWS); i++) {
            Map<String, Integer> columns = mapColumns(rows.get(i), profile.headers());
            boolean hasAmounts = (columns.containsKey("withdrawal") && columns.containsKey("deposit"))
                    || columns.containsKey("amount");
            if (columns.containsKey("date") && columns.containsKey("narration") && hasAmounts) {
                return Optional.of(new Header(i, columns));
            }
        }
        return Optional.empty();
    }

    private Map<String, Integer> mapColumns(List<String> row, Map<String, List<String>> headerAliases) {
        List<String> normalized = row.stream().map(StatementParser::normalize).toList();
        Map<String, Integer> columns = new HashMap<>();
        Set<Integer> used = new HashSet<>();
        for (String role : ROLES) {
            for (String alias : headerAliases.getOrDefault(role, List.of())) {
                int idx = indexOfHeader(normalized, normalize(alias), used);
                if (idx >= 0) {
                    columns.put(role, idx);
                    used.add(idx);
                    break;
                }
            }
        }
        return columns;
    }

    private int indexOfHeader(List<String> cells, String alias, Set<Integer> used) {
        for (int i = 0; i < cells.size(); i++) {
            if (!used.contains(i) && cells.get(i).equals(alias)) return i;
        }
        if (alias.length() >= 5) {
            for (int i = 0; i < cells.size(); i++) {
                if (!used.contains(i) && cells.get(i).startsWith(alias)) return i;
            }
        }
        return -1;
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    // ---------------------------------------------------------------- cell helpers

    private static String cell(List<String> row, Header h, String role) {
        Integer idx = h.columns().get(role);
        return idx == null || idx >= row.size() ? "" : row.get(idx).trim();
    }

    private static boolean isBlank(List<String> row) {
        return row.stream().allMatch(String::isBlank);
    }

    private static boolean isStarSeparator(List<String> row) {
        return row.stream().filter(s -> !s.isBlank()).allMatch(s -> s.chars().allMatch(c -> c == '*'));
    }

    private static List<DateTimeFormatter> formatters(BankProfile profile) {
        List<DateTimeFormatter> list = new ArrayList<>();
        list.add(DateTimeFormatter.ISO_LOCAL_DATE);
        for (String pattern : profile.dateFormats()) {
            list.add(new DateTimeFormatterBuilder().parseCaseInsensitive().appendPattern(pattern)
                    .toFormatter(Locale.ENGLISH));
        }
        return list;
    }

    static LocalDate parseDate(String raw, List<DateTimeFormatter> formats) {
        if (raw == null || raw.isBlank()) return null;
        String value = raw.trim();
        // some exports append a time ("01/02/2024 10:15:00")
        int space = value.indexOf(' ');
        List<String> candidates = space > 0 && value.indexOf(':') > space
                ? List.of(value, value.substring(0, space)) : List.of(value);
        for (String candidate : candidates) {
            for (DateTimeFormatter f : formats) {
                try {
                    return LocalDate.parse(candidate, f);
                } catch (DateTimeParseException ignored) {
                    // try next
                }
            }
        }
        return null;
    }
}
