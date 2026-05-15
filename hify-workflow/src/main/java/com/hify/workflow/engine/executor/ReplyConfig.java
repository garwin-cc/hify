package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

public record ReplyConfig(
        String content
) implements NodeConfigDef {
}
