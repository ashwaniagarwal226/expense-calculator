package com.expense.calculator.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Spring AI's Google GenAI auto-configuration refuses to start without credentials. The app must
 * keep working (rules-only categorisation, heuristic savings tips) when no Gemini key is set, so
 * when the key is blank we switch the Spring AI chat/embedding model selection to "none".
 * Setting {@code spring.ai.model.chat} explicitly always wins.
 */
public class AiAvailabilityEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String API_KEY = "spring.ai.google.genai.api-key";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if (StringUtils.hasText(env.getProperty(API_KEY)) || env.containsProperty("spring.ai.model.chat")) {
            return;
        }
        env.getPropertySources().addFirst(new MapPropertySource("geminiDisabled", Map.of(
                "spring.ai.model.chat", "none",
                "spring.ai.model.embedding", "none",
                "spring.ai.model.embedding.text", "none",
                "spring.ai.model.embedding.multimodal", "none")));
    }

    @Override
    public int getOrder() {
        // after application.properties / env vars have been loaded
        return Ordered.LOWEST_PRECEDENCE;
    }
}
