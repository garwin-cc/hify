package com.hify.workflow.domain.config;

import java.util.List;

public record HumanReviewNodeConfig(
        String title,
        String content,
        List<String> actions,
        Boolean allowEdit,
        String outputVariable
) implements NodeConfig {
}
