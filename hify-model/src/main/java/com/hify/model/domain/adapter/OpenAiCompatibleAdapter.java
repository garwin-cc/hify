package com.hify.model.domain.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.http.LlmHttpClient;
import org.springframework.stereotype.Component;

/** OPENAI_COMPATIBLE：与 OpenAI 协议完全一致，直接复用父类逻辑。 */
@Component
public class OpenAiCompatibleAdapter extends OpenAiAdapter {

    public OpenAiCompatibleAdapter(LlmHttpClient llmHttpClient, ObjectMapper objectMapper) {
        super(llmHttpClient, objectMapper);
    }
}
