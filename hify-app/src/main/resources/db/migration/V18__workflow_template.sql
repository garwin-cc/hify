-- V18: 轻量工作流模板，模板只用于复制创建真实 workflow，不参与执行。

ALTER TABLE t_workflow
    ADD COLUMN template_id BIGINT NULL COMMENT '来源工作流模板 ID' AFTER start_node_key,
    ADD INDEX idx_template_deleted (template_id, deleted);

CREATE TABLE IF NOT EXISTS t_workflow_template (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL COMMENT '模板名称',
    description VARCHAR(500) NOT NULL DEFAULT '' COMMENT '模板说明',
    category    VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '模板分类',
    icon        VARCHAR(50)  NOT NULL DEFAULT '' COMMENT '前端展示图标',
    config_json MEDIUMTEXT   NOT NULL COMMENT '完整工作流模板 JSON',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
    builtin     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否内置模板',
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    created_by  BIGINT       NOT NULL DEFAULT 0,
    deleted     TINYINT(1)   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    INDEX idx_enabled_category_deleted (enabled, category, deleted),
    INDEX idx_name_deleted (name, deleted)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '工作流模板';

INSERT INTO t_workflow_template
    (name, description, category, icon, config_json, enabled, builtin)
VALUES
    (
        '智能客服分类工作流',
        '识别用户意图，并按退款或通用路径生成客服回复',
        '客服',
        'service',
        '{"version":"1.0","workflow":{"name":"智能客服分类工作流","description":"识别用户意图，并按退款或通用路径生成客服回复","status":"DRAFT"},"startNodeKey":"start","nodes":[{"nodeKey":"start","nodeType":"START","name":"开始","config":{},"positionX":80,"positionY":180},{"nodeKey":"classify_intent","nodeType":"LLM","name":"识别用户意图","config":{"modelConfigRef":"{{model.chat}}","prompt":"你是智能客服意图分类器。请根据用户消息判断意图，只返回 refund、order_status、human、other 之一。用户消息：{{start.userMessage}}","outputVariable":"intent"},"positionX":320,"positionY":180},{"nodeKey":"is_refund","nodeType":"CONDITION","name":"是否退款","config":{"expression":"{{classify_intent.intent}} == refund","outputVariable":"matched"},"positionX":580,"positionY":180},{"nodeKey":"refund_reply","nodeType":"LLM","name":"退款回复","config":{"modelConfigRef":"{{model.chat}}","prompt":"用户想办理退款。请礼貌说明退款所需信息，并引导用户提供订单号。用户消息：{{start.userMessage}}","outputVariable":"answer"},"positionX":840,"positionY":90},{"nodeKey":"general_reply","nodeType":"LLM","name":"通用回复","config":{"modelConfigRef":"{{model.chat}}","prompt":"你是智能客服。请根据用户消息给出简洁、礼貌、可执行的回复。用户消息：{{start.userMessage}}","outputVariable":"answer"},"positionX":840,"positionY":280},{"nodeKey":"end_refund","nodeType":"END","name":"结束（退款）","config":{"outputVariable":"refund_reply.answer"},"positionX":1100,"positionY":90},{"nodeKey":"end_general","nodeType":"END","name":"结束（通用）","config":{"outputVariable":"general_reply.answer"},"positionX":1100,"positionY":280}],"edges":[{"sourceNodeKey":"start","targetNodeKey":"classify_intent","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"classify_intent","targetNodeKey":"is_refund","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"is_refund","targetNodeKey":"refund_reply","edgeType":"CONDITION","conditionExpression":"true","sortOrder":0},{"sourceNodeKey":"is_refund","targetNodeKey":"general_reply","edgeType":"CONDITION","conditionExpression":"false","sortOrder":1},{"sourceNodeKey":"refund_reply","targetNodeKey":"end_refund","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"general_reply","targetNodeKey":"end_general","edgeType":"DEFAULT","sortOrder":0}],"requirements":{"models":[{"key":"model.chat","type":"CHAT","label":"聊天模型","required":true}],"knowledgeBases":[],"tools":[]}}',
        1,
        1
    ),
    (
        '知识库问答工作流',
        '先检索知识库，再基于资料生成回答',
        '知识库',
        'knowledge',
        '{"version":"1.0","workflow":{"name":"知识库问答工作流","description":"先检索知识库，再基于资料生成回答","status":"DRAFT"},"startNodeKey":"start","nodes":[{"nodeKey":"start","nodeType":"START","name":"开始","config":{},"positionX":80,"positionY":180},{"nodeKey":"search_knowledge","nodeType":"KNOWLEDGE","name":"检索知识库","config":{"knowledgeBaseRef":"{{knowledge.base}}","query":"{{start.userMessage}}","topK":3,"outputVariable":"context"},"positionX":320,"positionY":180},{"nodeKey":"answer_with_context","nodeType":"LLM","name":"基于资料回答","config":{"modelConfigRef":"{{model.chat}}","prompt":"请基于以下参考资料回答用户问题。如果资料中没有相关信息，直接说我没有找到相关资料，不要编造。参考资料：{{search_knowledge.context}} 用户问题：{{start.userMessage}}","outputVariable":"answer"},"positionX":580,"positionY":180},{"nodeKey":"end","nodeType":"END","name":"结束","config":{"outputVariable":"answer_with_context.answer"},"positionX":840,"positionY":180}],"edges":[{"sourceNodeKey":"start","targetNodeKey":"search_knowledge","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"search_knowledge","targetNodeKey":"answer_with_context","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"answer_with_context","targetNodeKey":"end","edgeType":"DEFAULT","sortOrder":0}],"requirements":{"models":[{"key":"model.chat","type":"CHAT","label":"聊天模型","required":true}],"knowledgeBases":[{"key":"knowledge.base","label":"业务知识库","required":true}],"tools":[]}}',
        1,
        1
    ),
    (
        '研发方案生成工作流',
        '把研发需求整理为方案、风险和测试计划',
        '研发',
        'code',
        '{"version":"1.0","workflow":{"name":"研发方案生成工作流","description":"把研发需求整理为方案、风险和测试计划","status":"DRAFT"},"startNodeKey":"start","nodes":[{"nodeKey":"start","nodeType":"START","name":"开始","config":{},"positionX":80,"positionY":260},{"nodeKey":"requirement_summary","nodeType":"LLM","name":"需求整理","config":{"modelConfigRef":"{{model.chat}}","prompt":"你是研发需求分析助手。请把用户输入整理为结构化需求，包含背景、目标、本期范围、不做什么、验收标准。用户需求：{{start.userMessage}}","outputVariable":"summary"},"positionX":320,"positionY":260},{"nodeKey":"technical_design","nodeType":"LLM","name":"技术方案","config":{"modelConfigRef":"{{model.chat}}","prompt":"你是 Spring Boot 和 Vue 架构师。请基于以下需求生成技术方案，包含数据模型、接口、核心流程、异常处理和测试建议。需求：{{requirement_summary.summary}}","outputVariable":"design"},"positionX":580,"positionY":260},{"nodeKey":"risk_review","nodeType":"LLM","name":"风险评估","config":{"modelConfigRef":"{{model.chat}}","prompt":"请审查以下技术方案的风险，重点看数据一致性、权限、外部服务失败、回滚和可观测性。技术方案：{{technical_design.design}}","outputVariable":"risks"},"positionX":840,"positionY":260},{"nodeKey":"test_plan","nodeType":"LLM","name":"测试方案","config":{"modelConfigRef":"{{model.chat}}","prompt":"请基于需求、技术方案和风险，生成单元测试、集成测试、API 测试、前端验证和冒烟测试清单。需求：{{requirement_summary.summary}} 技术方案：{{technical_design.design}} 风险：{{risk_review.risks}}","outputVariable":"tests"},"positionX":1100,"positionY":260},{"nodeKey":"end","nodeType":"END","name":"结束","config":{"outputVariable":"test_plan.tests"},"positionX":1360,"positionY":260}],"edges":[{"sourceNodeKey":"start","targetNodeKey":"requirement_summary","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"requirement_summary","targetNodeKey":"technical_design","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"technical_design","targetNodeKey":"risk_review","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"risk_review","targetNodeKey":"test_plan","edgeType":"DEFAULT","sortOrder":0},{"sourceNodeKey":"test_plan","targetNodeKey":"end","edgeType":"DEFAULT","sortOrder":0}],"requirements":{"models":[{"key":"model.chat","type":"CHAT","label":"聊天模型","required":true}],"knowledgeBases":[],"tools":[]}}',
        1,
        1
    );
