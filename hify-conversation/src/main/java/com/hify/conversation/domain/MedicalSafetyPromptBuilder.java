package com.hify.conversation.domain;

import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

class MedicalSafetyPromptBuilder {

    static final String TEMPLATE_MARKER = "HIFY_MEDICAL_ASSISTANT";

    private static final List<String> MEDICAL_PROMPT_KEYWORDS = List.of(
            TEMPLATE_MARKER.toLowerCase(Locale.ROOT),
            "医疗辅助诊断",
            "医生辅助诊断",
            "医学辅助",
            "临床辅助");

    private static final List<String> RED_FLAG_KEYWORDS = List.of(
            "胸痛",
            "呼吸困难",
            "意识障碍",
            "昏迷",
            "卒中",
            "中风",
            "偏瘫",
            "严重过敏",
            "过敏性休克",
            "大出血",
            "自杀",
            "抽搐",
            "休克");

    private static final List<String> PRESCRIPTION_KEYWORDS = List.of(
            "开药",
            "处方",
            "改药",
            "停药",
            "剂量",
            "用量",
            "吃多少",
            "服用多少");

    String apply(String systemPrompt, String latestUserMessage, boolean hasKnowledgeBase) {
        String basePrompt = systemPrompt == null ? "" : systemPrompt;
        if (!isMedicalAgent(basePrompt)) {
            return basePrompt;
        }

        StringBuilder builder = new StringBuilder(basePrompt.trim());
        builder.append("\n\n【医疗安全边界】\n")
                .append("- 仅供医生参考，不替代面诊、最终诊断、处方或治疗决定。\n")
                .append("- 不要输出最终诊断，只能给出可能诊断方向、支持依据、反对依据和需补充信息。\n")
                .append("- 不得开药、调整剂量、停药或给出可直接执行的治疗指令。\n")
                .append("- 涉及医学依据时优先引用已绑定知识库或指南资料；资料不足时明确说明不确定。");

        if (!hasKnowledgeBase) {
            builder.append("\n\n【知识库约束】\n")
                    .append("当前 Agent 未绑定医学知识库，只能基于用户提供的信息做结构化整理和风险提示，不能声称已引用内部资料或指南。");
        }

        String normalizedMessage = normalize(latestUserMessage);
        if (containsAny(normalizedMessage, RED_FLAG_KEYWORDS)) {
            builder.append("\n\n【红旗症状提醒】\n")
                    .append("用户输入包含急危重症线索，必须优先提示立即联系医生或急诊，并说明需要尽快评估生命体征和危险因素。");
        }

        if (containsAny(normalizedMessage, PRESCRIPTION_KEYWORDS)) {
            builder.append("\n\n【处方和用药限制】\n")
                    .append("用户输入涉及处方或用药调整，只能提示由具备资质的医生结合病史、检查和禁忌证判断，不得给出具体药名、剂量或停换药指令。");
        }

        return builder.toString();
    }

    private boolean isMedicalAgent(String systemPrompt) {
        String normalizedPrompt = normalize(systemPrompt);
        return containsAny(normalizedPrompt, MEDICAL_PROMPT_KEYWORDS);
    }

    private boolean containsAny(String content, List<String> keywords) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        for (String keyword : keywords) {
            if (content.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String content) {
        return content == null ? "" : content.toLowerCase(Locale.ROOT);
    }
}
