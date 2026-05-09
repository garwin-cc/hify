package com.hify.workflow.domain.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hify.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NodeConfigParserTest {

    private final NodeConfigParser parser = new NodeConfigParser(new ObjectMapper());

    @Test
    void parsesLlmConfigIntoTypedRecord() throws Exception {
        NodeConfig config = parser.parse("LLM", new ObjectMapper().readTree("""
                {
                  "prompt": "提取订单号",
                  "modelConfigId": 1,
                  "outputVariable": "answer",
                  "temperature": 0.2,
                  "maxTokens": 256
                }
                """));

        assertThat(config).isInstanceOf(LlmNodeConfig.class);
        LlmNodeConfig llm = (LlmNodeConfig) config;
        assertThat(llm.prompt()).isEqualTo("提取订单号");
        assertThat(llm.modelConfigId()).isEqualTo(1L);
        assertThat(llm.outputVariable()).isEqualTo("answer");
        assertThat(llm.maxTokens()).isEqualTo(256);
    }

    @Test
    void parsesConditionConfigIntoTypedRecord() throws Exception {
        NodeConfig config = parser.parse("CONDITION", new ObjectMapper().readTree("""
                {
                  "expression": "'{{classify.intent}}' == 'refund'",
                  "outputVariable": "matched"
                }
                """));

        assertThat(config).isInstanceOf(ConditionNodeConfig.class);
        ConditionNodeConfig condition = (ConditionNodeConfig) config;
        assertThat(condition.expression()).isEqualTo("'{{classify.intent}}' == 'refund'");
        assertThat(condition.outputVariable()).isEqualTo("matched");
    }

    @Test
    void parsesEndConfigIntoTypedRecord() throws Exception {
        NodeConfig config = parser.parse("END", new ObjectMapper().readTree("""
                {
                  "outputVariable": "llm.answer"
                }
                """));

        assertThat(config).isInstanceOf(EndNodeConfig.class);
        EndNodeConfig end = (EndNodeConfig) config;
        assertThat(end.outputVariable()).isEqualTo("llm.answer");
    }

    @Test
    void rejectsUnsupportedNodeType() {
        assertThatThrownBy(() -> parser.parse("UNKNOWN", new ObjectMapper().createObjectNode()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的节点类型");
    }
}
