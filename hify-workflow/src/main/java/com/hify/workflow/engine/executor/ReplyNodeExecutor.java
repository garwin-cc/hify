package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.ExecutionContext;
import com.hify.workflow.engine.NodeConfigDef;
import com.hify.workflow.engine.WorkflowNode;
import org.springframework.stereotype.Component;

@Component
public class ReplyNodeExecutor extends AbstractNodeExecutor implements NodeExecutor {

    @Override
    public void execute(WorkflowNode node, NodeConfigDef config, ExecutionContext ctx) {
        ReplyConfig replyConfig = requireConfig(config, ReplyConfig.class);
        ctx.set(node.nodeKey(), "reply", ctx.resolve(replyConfig.content()));
    }

    @Override
    public String nodeType() {
        return "REPLY";
    }
}
