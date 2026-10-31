package com.expense.calculator.controller;

import com.expense.calculator.ai.GeminiClient;
import com.expense.calculator.ai.SavingsAdvisorService;
import com.expense.calculator.dto.AiStatus;
import com.expense.calculator.dto.InsightsResponse;
import com.expense.calculator.dto.SavingsAdvice;
import com.expense.calculator.service.InsightsService;
import com.expense.calculator.service.ProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("v1")
public class InsightsController {

    private final InsightsService insights;
    private final SavingsAdvisorService advisor;
    private final GeminiClient gemini;
    private final ProfileService profiles;

    public InsightsController(InsightsService insights, SavingsAdvisorService advisor, GeminiClient gemini,
                              ProfileService profiles) {
        this.insights = insights;
        this.advisor = advisor;
        this.gemini = gemini;
        this.profiles = profiles;
    }

    /** {@code profileId} omitted = whole household. */
    @GetMapping("/insights")
    public InsightsResponse insights(@RequestParam(required = false) Long profileId,
                                     @RequestParam(required = false) String bank) {
        return insights.insights(profiles.checkOptional(profileId), bank, gemini.isEnabled());
    }

    /** Gemini-powered savings suggestions for the latest {@code months} months. */
    @GetMapping("/insights/savings")
    public SavingsAdvice savings(@RequestParam(required = false) Long profileId,
                                 @RequestParam(defaultValue = "3") int months,
                                 @RequestParam(required = false) String bank,
                                 @RequestParam(defaultValue = "false") boolean refresh) {
        return advisor.advise(profiles.checkOptional(profileId), months, bank, refresh);
    }

    @GetMapping("/ai/status")
    public AiStatus aiStatus() {
        return new AiStatus(gemini.isEnabled(), gemini.config().providerName(), gemini.model(),
                gemini.isEnabled() ? null : "Set the GEMINI_API_KEY environment variable and restart the backend.");
    }
}
