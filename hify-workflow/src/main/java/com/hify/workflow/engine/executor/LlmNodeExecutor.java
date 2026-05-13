package com.hify.workflow.engine.executor;

import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.model.api.ChatMessage;
import com.hify.model.api.ChatRequest;
import com.hify.model.api.ChatResponse;
import com.hify.model.domain.adapter.ProviderAdapter;
import com.hify.model.domain.adapter.ProviderAdapterFactory;
import com.hify.model.infra.ModelConfigMapper;
import com.hify.model.infra.ModelConfigPo;
import com.hify.model.infra.ProviderMapper;
import com.hify.model.infra.ProviderPo;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowCallTrace;
import com.hify.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LlmNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    private static final int WORKFLOW_LLM_TIMEOUT_SECONDS = 300;

    private final ModelConfigMapper modelConfigMapper;
    private final ProviderMapper providerMapper;
    private final ProviderAdapterFactory providerAdapterFactory;

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        try {
            LlmNodeConfig llmConfig = requireConfig(config, LlmNodeConfig.class);
            ModelConfigPo modelConfig = requireModelConfig(llmConfig.modelConfigId());
            ProviderPo provider = requireProvider(modelConfig.getProviderId());
            ProviderAdapter adapter = providerAdapterFactory.getAdapter(provider.getType());
            String prompt = ctx.resolve(llmConfig.prompt());

            ChatRequest request = ChatRequest.builder()
                    .modelId(modelConfig.getModelId())
                    .messages(List.of(ChatMessage.builder()
                            .role("user")
                            .content(prompt)
                            .build()))
                    .temperature(llmConfig.temperature())
                    .maxTokens(llmConfig.maxTokens())
                    .build();

            long startedAt = System.currentTimeMillis();
            ChatResponse response = adapter.chat(provider, request, WORKFLOW_LLM_TIMEOUT_SECONDS);
            ctx.recordCall(new WorkflowCallTrace(
                    "LLM",
                    provider.getName() + "/" + modelConfig.getModelId(),
                    java.util.Map.of("provider", provider.getName(), "modelId", modelConfig.getModelId()),
                    java.util.Map.of("finishReason", response == null ? "" : response.getFinishReason(),
                            "inputTokens", response == null ? 0 : response.getInputTokens(),
                            "outputTokens", response == null ? 0 : response.getOutputTokens()),
                    "SUCCESS",
                    null,
                    elapsed(startedAt)));
            ctx.set(node.nodeKey(), outputVariable(llmConfig.outputVariable()),
                    response == null ? null : response.getContent());
        } catch (Exception e) {
            ctx.recordCall(new WorkflowCallTrace(
                    "LLM",
                    "",
                    java.util.Map.of(),
                    java.util.Map.of(),
                    "FAILED",
                    e.getMessage(),
                    null));
            throw toExecuteException(node, e);
        }
    }

    @Override
    public String nodeType() {
        return "LLM";
    }

    private ModelConfigPo requireModelConfig(Long modelConfigId) {
        ModelConfigPo modelConfig = modelConfigMapper.selectById(modelConfigId);
        if (modelConfig == null || modelConfig.getEnabled() == null || modelConfig.getEnabled() != 1) {
            throw new BizException(ErrorCode.AGENT_MODEL_UNAVAILABLE,
                    "模型配置不存在或已禁用: " + modelConfigId);
        }
        return modelConfig;
    }

    private ProviderPo requireProvider(Long providerId) {
        ProviderPo provider = providerMapper.selectById(providerId);
        if (provider == null || provider.getEnabled() == null || provider.getEnabled() != 1) {
            throw new BizException(ErrorCode.LLM_PROVIDER_NOT_FOUND,
                    "模型提供商不存在或已禁用: " + providerId);
        }
        return provider;
    }

    private static int elapsed(long startedAt) {
        return (int) Math.min(Integer.MAX_VALUE, System.currentTimeMillis() - startedAt);
    }
}
