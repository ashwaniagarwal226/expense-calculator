package com.expense.calculator.service;

import com.expense.calculator.category.CategoryService;
import com.expense.calculator.domain.Category;
import com.expense.calculator.domain.CategoryKind;
import com.expense.calculator.domain.EpnxTransaction;
import com.expense.calculator.domain.Profile;
import com.expense.calculator.repository.ProfileRepository;
import com.expense.calculator.dto.InsightsResponse;
import com.expense.calculator.dto.InsightsResponse.*;
import com.expense.calculator.dto.LegacyMonthSummary;
import com.expense.calculator.dto.TransactionDto;
import com.expense.calculator.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Computes dashboard insights in Java over the stored transactions (a personal data set is small),
 * which also keeps the queries database-independent (the old native query was H2-specific).
 */
@Service
public class InsightsService {

    private static final int RECURRING_MIN_MONTHS = 3;
    private static final double RECURRING_MAX_VARIATION = 0.25;

    private final TransactionRepository repository;
    private final CategoryService categories;
    private final ProfileRepository profiles;

    public InsightsService(TransactionRepository repository, CategoryService categories, ProfileRepository profiles) {
        this.repository = repository;
        this.categories = categories;
        this.profiles = profiles;
    }

    /**
     * @param profileId null = everyone (household view)
     * @param bank      null = all banks
     */
    @Transactional(readOnly = true)
    public InsightsResponse insights(Long profileId, String bank, boolean aiEnabled) {
        List<EpnxTransaction> txns = load(profileId, bank);
        Map<Long, Profile> people = profiles.findAll().stream().collect(Collectors.toMap(Profile::getId, p -> p));
        Map<YearMonth, List<EpnxTransaction>> byMonth = txns.stream()
                .collect(Collectors.groupingBy(t -> YearMonth.from(t.getTransactionDate()), TreeMap::new, Collectors.toList()));

        List<MonthInsight> months = new ArrayList<>();
        MonthInsight previous = null;
        for (Map.Entry<YearMonth, List<EpnxTransaction>> e : byMonth.entrySet()) {
            MonthInsight m = month(e.getKey(), e.getValue(), previous, people);
            months.add(m);
            previous = m;
        }
        Collections.reverse(months);

        List<String> banks = txns.stream().map(EpnxTransaction::getBank).filter(Objects::nonNull).distinct().sorted().toList();
        return new InsightsResponse(months, overall(months, txns, banks), aiEnabled);
    }

    /** Monthly totals in the format the original UI consumed. */
    @Transactional(readOnly = true)
    public List<LegacyMonthSummary> legacySummary() {
        return insights(null, null, false).months().stream().map(m -> new LegacyMonthSummary(
                m.label(), m.year(), m.totalSpent(),
                m.categories().stream().map(c -> new LegacyMonthSummary.Item(c.code(), c.amount())).toList())).toList();
    }

    @Transactional(readOnly = true)
    public List<EpnxTransaction> load(Long profileId, String bank) {
        List<EpnxTransaction> base = profileId == null ? repository.findAll() : repository.findByProfileId(profileId);
        return bank == null || bank.isBlank() ? base : base.stream().filter(t -> bank.equals(t.getBank())).toList();
    }

    private MonthInsight month(YearMonth ym, List<EpnxTransaction> txns, MonthInsight previous, Map<Long, Profile> people) {
        BigDecimal spent = BigDecimal.ZERO, income = BigDecimal.ZERO, invested = BigDecimal.ZERO,
                transfers = BigDecimal.ZERO, essential = BigDecimal.ZERO, discretionary = BigDecimal.ZERO;
        Map<String, BigDecimal> byCategory = new HashMap<>();
        Map<String, Integer> countByCategory = new HashMap<>();
        Map<String, BigDecimal> byMerchant = new HashMap<>();
        Map<String, Integer> countByMerchant = new HashMap<>();
        Map<String, String> merchantCategory = new HashMap<>();
        BigDecimal[] daily = filled(ym.lengthOfMonth());
        BigDecimal[] weekday = filled(7);
        int uncategorized = 0;

        for (EpnxTransaction t : txns) {
            Category c = categories.get(t.getTransactionType());
            if (Category.OTHERS.equals(c.getCode())) uncategorized++;
            if (t.getDepositAmt() != null && c.getKind() != CategoryKind.TRANSFER) {
                income = income.add(t.getDepositAmt());
            }
            if (!t.isDebit()) continue;
            BigDecimal amt = t.getWithdrawAmt();
            spent = spent.add(amt);
            byCategory.merge(c.getCode(), amt, BigDecimal::add);
            countByCategory.merge(c.getCode(), 1, Integer::sum);
            switch (c.getKind()) {
                case INVESTMENT -> invested = invested.add(amt);
                case TRANSFER -> transfers = transfers.add(amt);
                default -> {
                    if (c.isEssential()) essential = essential.add(amt); else discretionary = discretionary.add(amt);
                    LocalDate d = t.getTransactionDate().toLocalDate();
                    daily[d.getDayOfMonth() - 1] = daily[d.getDayOfMonth() - 1].add(amt);
                    weekday[d.getDayOfWeek().getValue() - 1] = weekday[d.getDayOfWeek().getValue() - 1].add(amt);
                    String merchant = Optional.ofNullable(t.getMerchant()).orElse("UNKNOWN");
                    byMerchant.merge(merchant, amt, BigDecimal::add);
                    countByMerchant.merge(merchant, 1, Integer::sum);
                    merchantCategory.putIfAbsent(merchant, c.getCode());
                }
            }
        }
        BigDecimal expenses = spent.subtract(invested).subtract(transfers);
        Double savingsRate = income.signum() > 0
                ? income.subtract(expenses).divide(income, 4, RoundingMode.HALF_UP).doubleValue() : null;
        int daysElapsed = ym.equals(YearMonth.now()) ? LocalDate.now().getDayOfMonth() : ym.lengthOfMonth();

        Map<String, BigDecimal> prevByCategory = previous == null ? Map.of() : previous.categories().stream()
                .collect(Collectors.toMap(CategoryBreakdown::code, CategoryBreakdown::amount));
        final BigDecimal spentTotal = spent;
        List<CategoryBreakdown> breakdown = byCategory.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(e -> {
                    Category c = categories.get(e.getKey());
                    BigDecimal prev = previous == null ? null : prevByCategory.getOrDefault(e.getKey(), BigDecimal.ZERO);
                    return new CategoryBreakdown(c.getCode(), c.getDisplayName(), c.getIcon(), c.getKind(), c.isEssential(),
                            round(e.getValue()), countByCategory.get(e.getKey()), share(e.getValue(), spentTotal),
                            prev == null ? null : round(prev), pctChange(prev, e.getValue()));
                }).toList();

        List<MerchantSpend> topMerchants = byMerchant.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(8)
                .map(e -> new MerchantSpend(e.getKey(), merchantCategory.get(e.getKey()), round(e.getValue()),
                        countByMerchant.get(e.getKey()))).toList();

        List<TransactionDto> largest = txns.stream().filter(EpnxTransaction::isDebit)
                .sorted(Comparator.comparing(EpnxTransaction::getWithdrawAmt).reversed()).limit(5)
                .map(t -> TransactionDto.of(t, categories.get(t.getTransactionType()))).toList();

        return new MonthInsight(ym.getYear(), ym.getMonthValue(), ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH),
                round(spent), round(income), round(invested), round(transfers), round(expenses),
                round(income.subtract(spent)), savingsRate, round(essential), round(discretionary),
                round(expenses.divide(BigDecimal.valueOf(Math.max(1, daysElapsed)), 2, RoundingMode.HALF_UP)),
                txns.size(), uncategorized, previous == null ? null : pctChange(previous.totalSpent(), spent),
                breakdown, topMerchants, largest,
                Arrays.stream(daily).map(InsightsService::round).toList(),
                Arrays.stream(weekday).map(InsightsService::round).toList(),
                byProfile(txns, people));
    }

    private List<ProfileSpend> byProfile(List<EpnxTransaction> txns, Map<Long, Profile> people) {
        Map<Long, List<EpnxTransaction>> grouped = txns.stream()
                .filter(t -> t.getProfileId() != null)
                .collect(Collectors.groupingBy(EpnxTransaction::getProfileId, TreeMap::new, Collectors.toList()));
        List<ProfileSpend> out = new ArrayList<>();
        grouped.forEach((id, list) -> {
            BigDecimal spent = BigDecimal.ZERO, expenses = BigDecimal.ZERO, income = BigDecimal.ZERO;
            for (EpnxTransaction t : list) {
                CategoryKind kind = categories.get(t.getTransactionType()).getKind();
                if (t.getDepositAmt() != null && kind != CategoryKind.TRANSFER) income = income.add(t.getDepositAmt());
                if (!t.isDebit()) continue;
                spent = spent.add(t.getWithdrawAmt());
                if (kind == CategoryKind.EXPENSE) expenses = expenses.add(t.getWithdrawAmt());
            }
            Profile p = people.get(id);
            out.add(new ProfileSpend(id, p == null ? "Unknown" : p.getName(), p == null ? "👤" : p.getEmoji(),
                    round(spent), round(expenses), round(income)));
        });
        out.sort(Comparator.comparing(ProfileSpend::spent).reversed());
        return out;
    }

    private Overall overall(List<MonthInsight> months, List<EpnxTransaction> txns, List<String> banks) {
        if (months.isEmpty()) {
            return new Overall(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, List.of(), List.of(), banks);
        }
        BigDecimal n = BigDecimal.valueOf(months.size());
        BigDecimal spent = months.stream().map(MonthInsight::totalSpent).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal expenses = months.stream().map(MonthInsight::expenses).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = months.stream().map(MonthInsight::income).reduce(BigDecimal.ZERO, BigDecimal::add);
        Double avgSavings = income.signum() > 0
                ? income.subtract(expenses).divide(income, 4, RoundingMode.HALF_UP).doubleValue() : null;

        Map<String, BigDecimal> catTotals = new HashMap<>();
        months.forEach(m -> m.categories().forEach(c -> catTotals.merge(c.code(), c.amount(), BigDecimal::add)));
        List<CategoryAverage> top = catTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(10)
                .map(e -> {
                    Category c = categories.get(e.getKey());
                    return new CategoryAverage(c.getCode(), c.getDisplayName(), c.getIcon(), c.isEssential(),
                            e.getValue().divide(n, 2, RoundingMode.HALF_UP), round(e.getValue()));
                }).toList();

        return new Overall(months.size(), spent.divide(n, 2, RoundingMode.HALF_UP),
                expenses.divide(n, 2, RoundingMode.HALF_UP), income.divide(n, 2, RoundingMode.HALF_UP),
                avgSavings, top, recurring(txns), banks);
    }

    /**
     * A merchant is "recurring" when it was paid in at least 3 distinct months and its monthly totals
     * vary by less than 25% (coefficient of variation) - rent, subscriptions, SIPs, house help ...
     */
    List<RecurringPayment> recurring(List<EpnxTransaction> txns) {
        Map<String, List<EpnxTransaction>> byMerchant = txns.stream()
                .filter(EpnxTransaction::isDebit)
                .filter(t -> t.getMerchant() != null && !"UNKNOWN".equals(t.getMerchant()))
                .filter(t -> categories.get(t.getTransactionType()).getKind() != CategoryKind.TRANSFER)
                .collect(Collectors.groupingBy(EpnxTransaction::getMerchant));
        List<RecurringPayment> out = new ArrayList<>();
        for (Map.Entry<String, List<EpnxTransaction>> e : byMerchant.entrySet()) {
            Map<YearMonth, BigDecimal> perMonth = e.getValue().stream().collect(Collectors.groupingBy(
                    t -> YearMonth.from(t.getTransactionDate()),
                    Collectors.reducing(BigDecimal.ZERO, EpnxTransaction::getWithdrawAmt, BigDecimal::add)));
            if (perMonth.size() < RECURRING_MIN_MONTHS) continue;
            double[] values = perMonth.values().stream().mapToDouble(BigDecimal::doubleValue).toArray();
            double mean = Arrays.stream(values).average().orElse(0);
            double sd = Math.sqrt(Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum() / values.length);
            if (mean <= 0 || sd / mean > RECURRING_MAX_VARIATION) continue;
            EpnxTransaction latest = e.getValue().stream().max(Comparator.comparing(EpnxTransaction::getTransactionDate)).get();
            Category c = categories.get(latest.getTransactionType());
            BigDecimal avg = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);
            out.add(new RecurringPayment(e.getKey(), c.getCode(), c.getDisplayName(), avg, perMonth.size(),
                    latest.getTransactionDate().toLocalDate(), avg.multiply(BigDecimal.valueOf(12))));
        }
        out.sort(Comparator.comparing(RecurringPayment::avgAmount).reversed());
        return out.size() > 15 ? out.subList(0, 15) : out;
    }

    // ---------------------------------------------------------------- math helpers

    private static BigDecimal[] filled(int n) {
        BigDecimal[] arr = new BigDecimal[n];
        Arrays.fill(arr, BigDecimal.ZERO);
        return arr;
    }

    static BigDecimal round(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP);
    }

    private static double share(BigDecimal part, BigDecimal total) {
        return total.signum() == 0 ? 0 : part.divide(total, 4, RoundingMode.HALF_UP).doubleValue();
    }

    /** % change from {@code before} to {@code after}; null when there is no meaningful base. */
    static Double pctChange(BigDecimal before, BigDecimal after) {
        if (before == null || before.signum() == 0) return null;
        return after.subtract(before).divide(before, 4, RoundingMode.HALF_UP).doubleValue();
    }
}
