package com.expense.calculator.ai;

import com.expense.calculator.domain.Category;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Asks Gemini to classify narrations that no keyword rule matched. Narrations are sent in batches;
 * the reply is bound to {@link Reply} by Spring AI's structured-output converter.
 */
@Slf4j
@Component
public class AiCategorizer {

    /** What we send per transaction. {@code id} lets us map answers back regardless of order. */
    public record Item(int id, String narration, String note, String direction, BigDecimal amount) {
    }

    public record Answer(int id, String category, String merchant, double confidence) {
    }

    public record Reply(List<Answer> results) {
    }

    private static final String SYSTEM_PROMPT = """
            You classify Indian bank-statement lines for a personal expense tracker.
            For every transaction pick exactly ONE category code from the allowed list.
            Rules:
            - Use only codes from the list, spelled exactly. If nothing fits, use OTHERS.
            - Narrations are raw bank text (UPI/NEFT/IMPS/POS prefixes, VPA handles, IFSC codes, reference numbers).
              Look for the merchant or payee name inside them.
            - note is what the account holder typed in their UPI app when paying (e.g. "TAXI", "MEDS", "FOOD").
              When present it is the strongest signal of purpose - prefer it over the payee name.
            - direction DEBIT means money left the account, CREDIT means money came in.
              Credits are usually SALARY, INTEREST, REFUND or SELF_TRANSFER; never classify a credit as a spend category
              unless it is clearly a refund from that merchant (then REFUND).
            - Payments to a person's name with no merchant hint: use OTHERS unless the amount/pattern makes the purpose obvious.
            - merchant: the short clean merchant/payee name as it appears in the narration (e.g. "SWIGGY", "BLINKIT",
              "RAHUL SHARMA"), upper case, no reference numbers. Must be a substring of the narration when possible.
            - confidence: 0.0-1.0, how sure you are about the category.
            Return a result for every id you were given.
            """;

    private final GeminiClient gemini;

    public AiCategorizer(GeminiClient gemini) {
        this.gemini = gemini;
    }

    public boolean isEnabled() {
        return gemini.isEnabled();
    }

    /**
     * @return answers keyed by item id; items Gemini skipped or answered with an unknown code are absent.
     * @throws AiUnavailableException if AI is disabled or the call fails
     */
    public Map<Integer, Answer> categorize(List<Item> items, Collection<Category> categories) {
        ChatClient client = gemini.client().orElseThrow(() -> new AiUnavailableException("Gemini is not configured"));
        Set<String> allowed = categories.stream().map(Category::getCode).collect(Collectors.toSet());
        String categoryList = categories.stream()
                .map(c -> "- " + c.getCode() + " (" + c.getKind() + "): " + c.getDisplayName()
                        + (c.getDescription() == null ? "" : " - " + c.getDescription()))
                .collect(Collectors.joining("\n"));

        Map<Integer, Answer> answers = new HashMap<>();
        int batchSize = Math.max(1, gemini.config().batchSize());
        for (int from = 0; from < items.size(); from += batchSize) {
            List<Item> batch = items.subList(from, Math.min(items.size(), from + batchSize));
            String txns = batch.stream()
                    .map(i -> i.id() + " | " + i.direction() + " | " + i.amount().toPlainString() + " | "
                            + (i.note() == null || i.note().isBlank() ? "-" : i.note()) + " | " + i.narration())
                    .collect(Collectors.joining("\n"));
            Reply reply;
            try {
                reply = client.prompt()
                        .system(SYSTEM_PROMPT)
                        .user(u -> u.text("""
                                Allowed categories:
                                {categories}

                                Transactions (id | direction | amount INR | note | narration):
                                {transactions}
                                """).param("categories", categoryList).param("transactions", txns))
                        .call()
                        .entity(Reply.class);
            } catch (RuntimeException e) {
                throw new AiUnavailableException("Gemini call failed: " + e.getMessage(), e);
            }
            if (reply == null || reply.results() == null) continue;
            for (Answer a : reply.results()) {
                String code = a.category() == null ? null : a.category().trim().toUpperCase(Locale.ROOT);
                if (code != null && allowed.contains(code)) {
                    answers.put(a.id(), new Answer(a.id(), code, a.merchant(), a.confidence()));
                } else {
                    log.debug("Gemini returned unknown category '{}' for item {}", a.category(), a.id());
                }
            }
        }
        return answers;
    }
}
