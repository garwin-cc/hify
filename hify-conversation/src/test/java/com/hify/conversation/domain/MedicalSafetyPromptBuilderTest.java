package com.hify.conversation.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalSafetyPromptBuilderTest {

    private final MedicalSafetyPromptBuilder builder = new MedicalSafetyPromptBuilder();

    @Test
    void should_appendMedicalSafetyBoundary_when_agentPromptIsMedicalTemplate() {
        String prompt = builder.apply("你是 HIFY_MEDICAL_ASSISTANT 医生辅助诊断助手", "咳嗽三天", true);

        assertThat(prompt).contains("【医疗安全边界】");
        assertThat(prompt).contains("仅供医生参考");
        assertThat(prompt).contains("不要输出最终诊断");
    }

    @Test
    void should_appendEmergencyEscalation_when_userMentionsRedFlagSymptom() {
        String prompt = builder.apply("你是医疗辅助诊断助手", "患者胸痛并呼吸困难", true);

        assertThat(prompt).contains("【红旗症状提醒】");
        assertThat(prompt).contains("立即联系医生或急诊");
    }

    @Test
    void should_appendKnowledgeWarning_when_medicalAgentHasNoKnowledgeBase() {
        String prompt = builder.apply("你是医疗辅助诊断助手", "头痛怎么办", false);

        assertThat(prompt).contains("【知识库约束】");
        assertThat(prompt).contains("当前 Agent 未绑定医学知识库");
    }

    @Test
    void should_keepPromptUnchanged_when_agentPromptIsNotMedical() {
        String basePrompt = "你是客服助手";

        String prompt = builder.apply(basePrompt, "我要改药量", false);

        assertThat(prompt).isEqualTo(basePrompt);
    }
}
