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
        return switch (type == null ? "" : type.toUpperCase()) {
            case "ANTHROPIC"         -> anthropicAdapter;
            case "OLLAMA"            -> ollamaAdapter;
            case "OPENAI_COMPATIBLE", "ALIBABA", "DEEPSEEK", "MODEL_GATEWAY" -> openAiCompatibleAdapter;
            default                  -> openAiAdapter;
        };
    }
}
