package com.hify.workflow.engine.executor;

import com.hify.workflow.engine.NodeConfigDef;

import java.util.List;

public record HumanReviewConfig(
        String title,
        String content,
        List<String> actions,
        Boolean allowEdit,
        String outputVariable
) implements NodeConfigDef {
}
