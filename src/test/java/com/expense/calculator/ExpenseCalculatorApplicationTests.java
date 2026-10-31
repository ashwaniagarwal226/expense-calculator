package com.expense.calculator;

import com.expense.calculator.ai.GeminiClient;
import com.expense.calculator.repository.CategoryRuleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack without a Gemini key: rules-only categorisation, heuristic savings tips. */
@SpringBootTest(properties = "spring.ai.google.genai.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExpenseCalculatorApplicationTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    GeminiClient gemini;
    @Autowired
    CategoryRuleRepository rules;

    static final String[][] JAN = {
            {"02/01/24", "UPI-SWIGGY-swiggy.upi@icici-ICIC0DC0099-400212345678-PAYMENT", "0000400212345678", "02/01/24", "450.00", "", "9550.00"},
            {"03/01/24", "NEFT CR-HERE SOLUTIONS INDIA PVT LTD-SALARY", "000000000000000", "03/01/24", "", "50000.00", "59550.00"},
            {"05/01/24", "UPI-RANDOM SHOP-random@okaxis-UTIB0000001-400512345678-NA", "0000400512345678", "05/01/24", "250.00", "", "59300.00"},
    };

    @Test
    void startsWithoutGeminiKey() {
        assertThat(gemini.isEnabled()).isFalse();
        assertThat(rules.count()).isGreaterThan(30);
    }

    @Test
    void uploadCategoriseInsightsAndDedupe() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "jan.xls", "application/vnd.ms-excel", TestStatements.hdfcXls(JAN));

        mvc.perform(multipart("/v1/statements/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bank").value("HDFC"))
                .andExpect(jsonPath("$.bankAutoDetected").value(true))
                .andExpect(jsonPath("$.inserted").value(3))
                .andExpect(jsonPath("$.ruleCategorized").value(2))
                .andExpect(jsonPath("$.uncategorized").value(1))
                .andExpect(jsonPath("$.aiEnabled").value(false));

        // same file again -> updates, no duplicates (also for the all-zero ref number line)
        mvc.perform(multipart("/v1/hdfc/transactionupload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(0))
                .andExpect(jsonPath("$.updated").value(3));

        mvc.perform(get("/v1/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.months", hasSize(1)))
                .andExpect(jsonPath("$.months[0].label").value("January"))
                .andExpect(jsonPath("$.months[0].totalSpent").value(700.0))
                .andExpect(jsonPath("$.months[0].income").value(50000.0))
                .andExpect(jsonPath("$.months[0].uncategorizedCount").value(1))
                .andExpect(jsonPath("$.months[0].categories[0].code").value("SWIGGY"));

        mvc.perform(get("/v1/hdfc/graphdata"))
                .andExpect(jsonPath("$[0].month").value("January"))
                .andExpect(jsonPath("$[0].totalSpent").value(700.0));

        mvc.perform(get("/v1/insights/savings?months=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("HEURISTIC"))
                .andExpect(jsonPath("$.tips", not(empty())));

        // manual override + remember as rule
        String id = mvc.perform(get("/v1/transactions?uncategorized=true"))
                .andExpect(jsonPath("$.total").value(1))
                .andReturn().getResponse().getContentAsString().replaceAll(".*\"id\":(\\d+).*", "$1");
        mvc.perform(patch("/v1/transactions/" + id + "/category").contentType("application/json")
                        .content("{\"category\":\"SHOPPING\",\"createRule\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transaction.categorySource").value("MANUAL"))
                .andExpect(jsonPath("$.ruleId").isNumber());
        mvc.perform(get("/v1/rules")).andExpect(jsonPath("$[?(@.keyword == 'RANDOM SHOP')].source").value("USER"));
    }

    @Test
    void rejectsUnknownFiles() throws Exception {
        MockMultipartFile junk = new MockMultipartFile("file", "x.csv", "text/csv", "a,b,c\n1,2,3".getBytes());
        mvc.perform(multipart("/v1/statements/upload").file(junk))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", containsString("Could not recognise the bank")));
    }
}
