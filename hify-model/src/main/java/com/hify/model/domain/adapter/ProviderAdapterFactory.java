package com.hify.model.domain.adapter;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProviderAdapterFactory {

    private final OpenAiAdapter            openAiAdapter;
    private final AnthropicAdapter         anthropicAdapter;
    private final OllamaAdapter            ollamaAdapter;
    private final OpenAiCompatibleAdapter  openAiCompatibleAdapter;

    public ProviderAdapter getAdapter(String type) {
        return switch (type) {
            case "ANTHROPIC"         -> anthropicAdapter;
            case "OLLAMA"            -> ollamaAdapter;
            case "OPENAI_COMPATIBLE", "ALIBABA" -> openAiCompatibleAdapter;
            default                  -> openAiAdapter;   // OPENAI, DEEPSEEK, 其他
        };
    }
}
