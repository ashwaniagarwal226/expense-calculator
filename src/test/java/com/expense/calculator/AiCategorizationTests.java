package com.expense.calculator;

import com.expense.calculator.domain.RuleSource;
import com.expense.calculator.repository.CategoryRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the Spring AI path end to end (prompt -> ChatModel -> structured output -> DB) with a
 * stub ChatModel standing in for Gemini.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiCategorizationTests {

    static final List<String> PROMPTS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class FakeGemini {
        @Bean
        ChatModel fakeGeminiChatModel() {
            return new ChatModel() {
                @Override
                public ChatResponse call(Prompt prompt) {
                    String text = prompt.getContents();
                    PROMPTS.add(text);
                    String json;
                    if (text.contains("Transactions (id")) {
                        StringBuilder sb = new StringBuilder("{\"results\":[");
                        Matcher m = Pattern.compile("(?m)^(\\d+) \\| (DEBIT|CREDIT) \\| [0-9.]+ \\| [^|]* \\| (.*)$").matcher(text);
                        boolean first = true;
                        while (m.find()) {
                            boolean mystery = m.group(3).contains("MYSTERY STORE");
                            if (!first) sb.append(',');
                            first = false;
                            sb.append("{\"id\":").append(m.group(1))
                                    .append(",\"category\":\"").append(mystery ? "GROCERIES" : "OTHERS")
                                    .append("\",\"merchant\":\"").append(mystery ? "MYSTERY STORE" : "")
                                    .append("\",\"confidence\":0.93}");
                        }
                        json = "```json\n" + sb.append("]}") + "\n```";
                    } else {
                        json = """
                                {"summary":"You save 40% of income.","estimatedMonthlySavings":500,
                                 "tips":[{"title":"Cook more","category":"SWIGGY","detail":"12 orders/month","currentMonthlySpend":2000,
                                          "suggestedMonthlyBudget":1500,"potentialMonthlySaving":500,"difficulty":"EASY"}],
                                 "quickWins":["Cancel unused OTT"]}
                                """;
                    }
                    return new ChatResponse(List.of(new Generation(new AssistantMessage(json))));
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    CategoryRuleRepository rules;

    @Test
    void unknownTransactionsAreClassifiedByGeminiAndLearnedAsRules() throws Exception {
        MockMultipartFile sbi = new MockMultipartFile("file", "sbi.csv", "text/csv", TestStatements.sbiCsv());
        mvc.perform(multipart("/v1/statements/upload").file(sbi).param("bank", "SBI"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiEnabled").value(true))
                .andExpect(jsonPath("$.ruleCategorized").value(2))   // BIGBASKET, SALARY
                .andExpect(jsonPath("$.aiCategorized").value(1))     // MYSTERY STORE
                .andExpect(jsonPath("$.uncategorized").value(0));

        assertThat(PROMPTS.get(0)).contains("GROCERIES").contains("MYSTERY STORE").doesNotContain("BIGBASKET/YESB");
        assertThat(rules.findAll()).anyMatch(r -> r.getKeyword().equals("MYSTERY STORE") && r.getSource() == RuleSource.AI);

        mvc.perform(get("/v1/transactions?category=GROCERIES"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[?(@.merchant == 'MYSTERY STORE')].categorySource").value("AI"));

        mvc.perform(get("/v1/insights/savings?months=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("GEMINI"))
                .andExpect(jsonPath("$.tips[0].title").value("Cook more"))
                .andExpect(jsonPath("$.estimatedMonthlySavings").value(500));

        mvc.perform(get("/v1/ai/status")).andExpect(jsonPath("$.enabled").value(true));
    }
}
