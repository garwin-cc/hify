package com.hify.workflow.engine.executor;

import com.hify.knowledge.api.KnowledgeSearchReq;
import com.hify.knowledge.api.KnowledgeSearchResp;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
public class KnowledgeNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    private static final int DEFAULT_TOP_K = 3;

    private final KnowledgeService knowledgeService;

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        try {
            KnowledgeConfig knowledgeConfig = requireConfig(config, KnowledgeConfig.class);
            String query = ctx.resolve(knowledgeConfig.query());

            KnowledgeSearchReq req = new KnowledgeSearchReq();
            req.setKnowledgeBaseIds(List.of(knowledgeConfig.knowledgeBaseId()));
            req.setQueryText(query);
            req.setTopK(knowledgeConfig.topK() == null ? DEFAULT_TOP_K : knowledgeConfig.topK());
            req.setSourceType("WORKFLOW");
            req.setSourceId(node.nodeKey());
            req.setIncludeTrace(true);

            List<KnowledgeSearchResp> chunks = knowledgeService.searchSimilar(req);
            ctx.set(node.nodeKey(), outputVariable(knowledgeConfig.outputVariable()), formatChunks(chunks));
        } catch (Exception e) {
            throw toExecuteException(node, e);
        }
    }

    @Override
    public String nodeType() {
        return "KNOWLEDGE";
    }

    private String formatChunks(List<KnowledgeSearchResp> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        return IntStream.range(0, chunks.size())
                .mapToObj(index -> "[" + (index + 1) + "] " + chunks.get(index).getContent())
                .collect(Collectors.joining("\n"));
    }
}
