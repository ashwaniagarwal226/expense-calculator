package com.expense.calculator.ai;

import com.expense.calculator.config.ExpenseProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Thin wrapper over the Spring AI {@link ChatModel}. Spring AI's Google GenAI starter creates the
 * ChatModel when {@code GEMINI_API_KEY} is set; otherwise there is no bean and every AI feature
 * reports itself as disabled instead of failing.
 */
@Component
public class GeminiClient {

    private final ChatClient chatClient;
    private final ExpenseProperties.Ai config;

    public GeminiClient(ObjectProvider<ChatModel> chatModel, ExpenseProperties properties) {
        this.config = properties.ai();
        ChatModel model = config.enabled() ? chatModel.getIfAvailable() : null;
        this.chatClient = model == null ? null : ChatClient.builder(model).build();
    }

    public boolean isEnabled() {
        return chatClient != null;
    }

    public Optional<ChatClient> client() {
        return Optional.ofNullable(chatClient);
    }

    public String model() {
        return config.model();
    }

    public ExpenseProperties.Ai config() {
        return config;
    }
}
