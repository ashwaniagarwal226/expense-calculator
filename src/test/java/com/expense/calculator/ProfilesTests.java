package com.expense.calculator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Several people's statements in one app, plus UPI-note categorisation. */
@SpringBootTest(properties = "spring.ai.google.genai.api-key=")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext
class ProfilesTests {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    static final String[][] STATEMENT = {
            {"02/01/24", "UPI-RAHUL SINGH-Q218750349@YBL-YESB0YBLUPI-907923059936-TAXI", "0000907923059936", "02/01/24", "300.00", "", "9700.00"},
            {"03/01/24", "UPI-AJAY KUMAR DAS-AD3664807@OKICICI-IPOS0000001-939345019396-MEDS", "0000939345019396", "03/01/24", "450.00", "", "9250.00"},
            {"04/01/24", "UPI-SUNITA KUMARI-sunita@ybl-YESB0YBLUPI-400412345678-PAYMENT FROM PHONE", "0000400412345678", "04/01/24", "4000.00", "", "5250.00"},
    };

    private long createProfile(String name, String emoji) throws Exception {
        String body = mvc.perform(post("/v1/profiles").contentType("application/json")
                        .content("{\"name\":\"" + name + "\",\"emoji\":\"" + emoji + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("id").asLong();
    }

    @Test
    void separateProfilesNotesAndHouseholdView() throws Exception {
        // the default profile always exists
        mvc.perform(get("/v1/profiles")).andExpect(jsonPath("$[0].name").value("Me"));
        long me = json.readTree(mvc.perform(get("/v1/profiles")).andReturn().getResponse().getContentAsString()).get(0).get("id").asLong();
        long wife = createProfile("Wife", "👩");
        mvc.perform(post("/v1/profiles").contentType("application/json").content("{\"name\":\"wife\"}"))
                .andExpect(status().isBadRequest());

        MockMultipartFile file = new MockMultipartFile("file", "jan.xls", "application/vnd.ms-excel", TestStatements.hdfcXls(STATEMENT));
        // same statement for both people: no clash on reference numbers
        mvc.perform(multipart("/v1/statements/upload").file(file).param("profileId", String.valueOf(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inserted").value(3))
                .andExpect(jsonPath("$.ruleCategorized").value(2)); // TAXI + MEDS from the UPI notes
        mvc.perform(multipart("/v1/statements/upload").file(file).param("profileId", String.valueOf(wife)))
                .andExpect(jsonPath("$.inserted").value(3));

        mvc.perform(get("/v1/transactions").param("profileId", String.valueOf(me)).param("q", "TAXI"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].remark").value("TAXI"))
                .andExpect(jsonPath("$.items[0].category").value("CAB_SERVICE"));
        mvc.perform(get("/v1/transactions").param("profileId", String.valueOf(me)).param("category", "MEDICAL"))
                .andExpect(jsonPath("$.total").value(1));

        // "remember" on my transaction creates a rule only for me
        JsonNode maid = json.readTree(mvc.perform(get("/v1/transactions").param("profileId", String.valueOf(me))
                .param("q", "SUNITA")).andReturn().getResponse().getContentAsString()).get("items").get(0);
        mvc.perform(patch("/v1/transactions/" + maid.get("id").asLong() + "/category").contentType("application/json")
                        .content("{\"category\":\"MAID\",\"createRule\":true}"))
                .andExpect(jsonPath("$.ruleId").isNumber());
        mvc.perform(post("/v1/transactions/recategorize").param("ai", "false"));
        mvc.perform(get("/v1/transactions").param("profileId", String.valueOf(wife)).param("category", "MAID"))
                .andExpect(jsonPath("$.total").value(0));

        // insights per person and for the household
        mvc.perform(get("/v1/insights").param("profileId", String.valueOf(wife)))
                .andExpect(jsonPath("$.months[0].totalSpent").value(4750.0))
                .andExpect(jsonPath("$.months[0].byProfile", hasSize(1)));
        mvc.perform(get("/v1/insights"))
                .andExpect(jsonPath("$.months[0].totalSpent").value(9500.0))
                .andExpect(jsonPath("$.months[0].byProfile", hasSize(2)));

        // deleting a profile removes its data and its own rules
        mvc.perform(delete("/v1/profiles/" + wife)).andExpect(jsonPath("$.transactionsDeleted").value(3));
        mvc.perform(get("/v1/insights")).andExpect(jsonPath("$.months[0].totalSpent").value(4750.0));
        mvc.perform(delete("/v1/profiles/" + me)).andExpect(status().isBadRequest()); // last profile stays
    }
}
