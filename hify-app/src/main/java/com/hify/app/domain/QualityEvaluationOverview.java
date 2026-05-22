package com.hify.app.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class QualityEvaluationOverview {

    private Summary summary = new Summary();
    private List<AgentQuality> agents = new ArrayList<>();
    private List<IssueQuality> issues = new ArrayList<>();

    @Data
    public static class Summary {
        private long feedbackCount;
        private long negativeFeedbackCount;
        private double negativeFeedbackRate;
        private long openSampleCount;
        private long ragFeedbackCount;
        private long ragHelpfulCount;
        private double ragHelpfulRate;
    }

    @Data
    public static class AgentQuality {
        private Long agentId;
        private String agentName;
        private long feedbackCount;
        private long negativeFeedbackCount;
        private double negativeFeedbackRate;
        private long openSampleCount;
    }

    @Data
    public static class IssueQuality {
        private String issueType;
        private long count;
        private double rate;
    }
}
