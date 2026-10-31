package com.expense.calculator.ai;

import com.expense.calculator.dto.InsightsResponse;
import com.expense.calculator.dto.InsightsResponse.CategoryBreakdown;
import com.expense.calculator.dto.InsightsResponse.MonthInsight;
import com.expense.calculator.dto.SavingsAdvice;
import com.expense.calculator.dto.SavingsAdvice.Tip;
import com.expense.calculator.domain.Category;
import com.expense.calculator.domain.CategoryKind;
import com.expense.calculator.service.InsightsService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * "Where can I save more?" - sends an aggregated spending profile (category totals per month, top
 * merchants, recurring payments; never raw narrations or reference numbers) to Gemini and returns
 * structured, actionable tips. Results are cached per (data, months) fingerprint so reopening the
 * page does not cost another API call. Falls back to local heuristics when Gemini is unavailable.
 */
@Slf4j
@Service
public class SavingsAdvisorService {

    /** What Gemini must return (bound by Spring AI's BeanOutputConverter). */
    record GeminiPlan(String summary, BigDecimal estimatedMonthlySavings, List<Tip> tips, List<String> quickWins) {
    }

    private static final String SYSTEM_PROMPT = """
            You are a pragmatic personal-finance coach for a salaried person in India. You get an aggregated
            spending profile in INR as JSON. Find the specific places where they can realistically save money.
            Guidelines:
            - Be concrete and quantitative: reference actual categories, merchants and amounts from the data.
            - Prioritise discretionary spend (essential=false), month-over-month spikes, frequent small orders
              (food delivery, quick commerce, cabs), overlapping subscriptions and recurring payments worth renegotiating.
            - Do not tell them to stop supporting family, skip rent, or stop investing; you may suggest optimising them.
            - INVESTMENT is money saved, not spent. TRANSFER is excluded from spending.
            - suggestedMonthlyBudget + potentialMonthlySaving should roughly equal currentMonthlySpend.
            - estimatedMonthlySavings = sum of potentialMonthlySaving across tips.
            - difficulty is one of EASY, MEDIUM, HARD.
            - Give 4 to 7 tips sorted by potentialMonthlySaving descending, and 3 to 5 one-line quickWins.
            - summary: 2-3 sentences on their overall spending health and savings rate.
            """;

    private final InsightsService insights;
    private final GeminiClient gemini;
    private final ObjectMapper mapper;
    private final Map<String, SavingsAdvice> cache = new ConcurrentHashMap<>();

    public SavingsAdvisorService(InsightsService insights, GeminiClient gemini, ObjectMapper mapper) {
        this.insights = insights;
        this.gemini = gemini;
        this.mapper = mapper;
    }

    /** @param profileId null = the whole household */
    public SavingsAdvice advise(Long profileId, int months, String bank, boolean refresh) {
        InsightsResponse data = insights.insights(profileId, bank, gemini.isEnabled());
        List<MonthInsight> window = data.months().stream().limit(Math.max(1, Math.min(months, 12))).toList();
        if (window.isEmpty()) {
            return new SavingsAdvice("HEURISTIC", null, "Upload a bank statement to get savings suggestions.",
                    BigDecimal.ZERO, List.of(), List.of(), 0, BigDecimal.ZERO, BigDecimal.ZERO, LocalDateTime.now(), null);
        }
        Map<String, Object> profile = profile(window, data.overall());
        String key = profileId + "|" + fingerprint(profile, months, bank);
        if (!refresh && cache.containsKey(key)) {
            return cache.get(key);
        }

        SavingsAdvice advice;
        if (gemini.isEnabled()) {
            try {
                advice = askGemini(profile, window);
            } catch (RuntimeException e) {
                log.warn("Gemini savings analysis failed, using heuristics: {}", e.getMessage());
                advice = heuristic(window, "Gemini call failed (" + e.getMessage() + "). Showing rule-based tips instead.");
            }
        } else {
            advice = heuristic(window, "Gemini is not configured - set GEMINI_API_KEY for personalised AI advice. Showing rule-based tips.");
        }
        if ("GEMINI".equals(advice.source())) {
            cache.put(key, advice);
        }
        return advice;
    }

    // ---------------------------------------------------------------- Gemini

    private SavingsAdvice askGemini(Map<String, Object> profile, List<MonthInsight> window) {
        String json;
        try {
            json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(profile);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        GeminiPlan plan = gemini.client().orElseThrow()
                .prompt()
                .system(SYSTEM_PROMPT)
                .user(u -> u.text("Spending profile:\n{profile}").param("profile", json))
                .call()
                .entity(GeminiPlan.class);
        if (plan == null || plan.tips() == null) {
            throw new AiUnavailableException("Empty response from Gemini");
        }
        List<Tip> tips = plan.tips().stream()
                .sorted(Comparator.comparing((Tip t) -> Optional.ofNullable(t.potentialMonthlySaving()).orElse(BigDecimal.ZERO)).reversed())
                .toList();
        BigDecimal total = tips.stream().map(Tip::potentialMonthlySaving).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new SavingsAdvice("GEMINI", gemini.model(), plan.summary(), total, tips,
                Optional.ofNullable(plan.quickWins()).orElse(List.of()), window.size(),
                avg(window, MonthInsight::income), avg(window, MonthInsight::expenses), LocalDateTime.now(), null);
    }

    /** Aggregates only - no narrations, account numbers or reference numbers leave the machine here. */
    Map<String, Object> profile(List<MonthInsight> window, InsightsResponse.Overall overall) {
        Map<String, Map<String, Object>> categories = new LinkedHashMap<>();
        for (MonthInsight m : window) {
            for (CategoryBreakdown c : m.categories()) {
                Map<String, Object> entry = categories.computeIfAbsent(c.code(), k -> {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("name", c.name());
                    v.put("kind", c.kind());
                    v.put("essential", c.essential());
                    v.put("monthly", new LinkedHashMap<String, BigDecimal>());
                    v.put("transactions", 0);
                    return v;
                });
                @SuppressWarnings("unchecked")
                Map<String, BigDecimal> monthly = (Map<String, BigDecimal>) entry.get("monthly");
                monthly.put(m.label() + " " + m.year(), c.amount());
                entry.put("transactions", (int) entry.get("transactions") + c.count());
            }
        }
        Map<String, BigDecimal> merchantTotals = new HashMap<>();
        Map<String, Integer> merchantCounts = new HashMap<>();
        Map<String, String> merchantCats = new HashMap<>();
        window.forEach(m -> m.topMerchants().forEach(t -> {
            merchantTotals.merge(t.merchant(), t.amount(), BigDecimal::add);
            merchantCounts.merge(t.merchant(), t.count(), Integer::sum);
            merchantCats.putIfAbsent(t.merchant(), t.category());
        }));
        List<Map<String, Object>> merchants = merchantTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(12)
                .map(e -> Map.<String, Object>of("merchant", e.getKey(), "category", String.valueOf(merchantCats.get(e.getKey())),
                        "total", e.getValue(), "payments", merchantCounts.get(e.getKey())))
                .toList();

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("monthsAnalyzed", window.stream().map(m -> m.label() + " " + m.year()).toList());
        profile.put("avgMonthlyIncome", avg(window, MonthInsight::income));
        profile.put("avgMonthlyExpenses", avg(window, MonthInsight::expenses));
        profile.put("avgMonthlyInvested", avg(window, MonthInsight::invested));
        profile.put("avgMonthlyEssential", avg(window, MonthInsight::essential));
        profile.put("avgMonthlyDiscretionary", avg(window, MonthInsight::discretionary));
        profile.put("savingsRateByMonth", window.stream().collect(Collectors.toMap(
                m -> m.label() + " " + m.year(), m -> m.savingsRate() == null ? "n/a" : String.format("%.0f%%", m.savingsRate() * 100),
                (a, b) -> a, LinkedHashMap::new)));
        profile.put("categories", categories);
        profile.put("topMerchants", merchants);
        profile.put("recurringPayments", overall.recurring().stream()
                .map(r -> Map.of("merchant", r.merchant(), "category", r.category(), "avgMonthly", r.avgAmount(),
                        "monthsSeen", r.monthsSeen()))
                .toList());
        return profile;
    }

    private String fingerprint(Map<String, Object> profile, int months, String bank) {
        try {
            return months + "|" + bank + "|" + mapper.writeValueAsString(profile).hashCode();
        } catch (JsonProcessingException e) {
            return UUID.randomUUID().toString();
        }
    }

    // ---------------------------------------------------------------- heuristic fallback

    SavingsAdvice heuristic(List<MonthInsight> window, String notice) {
        int n = window.size();
        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, CategoryBreakdown> meta = new HashMap<>();
        window.forEach(m -> m.categories().forEach(c -> {
            totals.merge(c.code(), c.amount(), BigDecimal::add);
            counts.merge(c.code(), c.count(), Integer::sum);
            meta.putIfAbsent(c.code(), c);
        }));

        List<Tip> tips = new ArrayList<>();
        // 1. trim the biggest discretionary categories by 20%
        totals.entrySet().stream()
                .filter(e -> !meta.get(e.getKey()).essential() && meta.get(e.getKey()).kind() == CategoryKind.EXPENSE)
                .filter(e -> !Category.OTHERS.equals(e.getKey())) // "cut Others" is not actionable
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed()).limit(4)
                .forEach(e -> {
                    CategoryBreakdown c = meta.get(e.getKey());
                    BigDecimal monthly = e.getValue().divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP);
                    double perMonth = counts.get(e.getKey()) / (double) n;
                    BigDecimal saving = monthly.multiply(BigDecimal.valueOf(0.2)).setScale(0, RoundingMode.HALF_UP);
                    if (saving.signum() <= 0) return;
                    int payments = counts.get(e.getKey());
                    String detail = String.format("You spend about ₹%,d a month on %s (%d payment%s in %d month%s). "
                                    + "Setting a monthly cap 20%% lower than today saves ₹%,d.",
                            monthly.longValue(), c.name(), payments, payments == 1 ? "" : "s", n, n == 1 ? "" : "s",
                            saving.longValue());
                    if (perMonth >= 12) {
                        detail += " With this many small payments, batching orders or planning ahead makes the biggest difference.";
                    }
                    tips.add(new Tip("Cap " + c.name(), c.code(), detail, monthly, monthly.subtract(saving), saving,
                            perMonth >= 12 ? "MEDIUM" : "EASY"));
                });
        // 2. latest month spikes vs the earlier average
        if (n >= 2) {
            MonthInsight latest = window.get(0);
            for (CategoryBreakdown c : latest.categories()) {
                if (c.essential() || c.kind() != CategoryKind.EXPENSE || Category.OTHERS.equals(c.code())) continue;
                BigDecimal earlier = totals.get(c.code()).subtract(c.amount())
                        .divide(BigDecimal.valueOf(n - 1), 0, RoundingMode.HALF_UP);
                if (earlier.signum() > 0 && c.amount().compareTo(earlier.multiply(BigDecimal.valueOf(1.3))) > 0
                        && tips.stream().noneMatch(t -> c.code().equals(t.category()))) {
                    BigDecimal saving = c.amount().subtract(earlier);
                    tips.add(new Tip(c.name() + " spiked in " + latest.label(), c.code(),
                            String.format("₹%,d in %s vs a ₹%,d average before. Getting back to your usual level saves ₹%,d.",
                                    c.amount().longValue(), latest.label(), earlier.longValue(), saving.longValue()),
                            c.amount(), earlier, saving, "EASY"));
                }
            }
        }
        tips.sort(Comparator.comparing(Tip::potentialMonthlySaving).reversed());
        BigDecimal total = tips.stream().map(Tip::potentialMonthlySaving).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal income = avg(window, MonthInsight::income);
        BigDecimal expenses = avg(window, MonthInsight::expenses);
        String summary = income.signum() > 0
                ? String.format("Over the last %d month(s) you earned ~₹%,d and spent ~₹%,d a month (excluding investments and transfers). "
                + "Trimming the discretionary categories below could free up ~₹%,d a month.", n, income.longValue(), expenses.longValue(), total.longValue())
                : String.format("Over the last %d month(s) you spent ~₹%,d a month. Trimming the categories below could free up ~₹%,d a month.",
                n, expenses.longValue(), total.longValue());
        return new SavingsAdvice("HEURISTIC", null, summary, total, tips,
                List.of("Review subscriptions you have not used in the last month",
                        "Set a weekly budget for food delivery and quick-commerce apps",
                        "Automate a SIP on salary day so savings happen before spending"),
                n, income, expenses, LocalDateTime.now(), notice);
    }

    private static BigDecimal avg(List<MonthInsight> window, java.util.function.Function<MonthInsight, BigDecimal> f) {
        return window.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(window.size()), 0, RoundingMode.HALF_UP);
    }
}
