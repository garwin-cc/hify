package com.hify.agent.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.agent.api.*;
import com.hify.agent.infra.AgentMapper;
import com.hify.agent.infra.AgentPo;
import com.hify.agent.infra.AgentToolMapper;
import com.hify.agent.infra.AgentToolPo;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.web.PageResult;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.mcp.api.McpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper       agentMapper;
    private final AgentToolMapper   agentToolMapper;
    // ModelConfigService 是 hify-model 的 api/ 接口，跨模块调用规范：只能通过 api/ 层
    private final ModelConfigService modelConfigService;
    private final McpService         mcpService;

    private static final int MAX_BOUND_TOOL_COUNT = 10;

    // ── 创建 ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", allEntries = true),
    })
    public AgentDetailResp create(CreateAgentReq req) {

        // Step 1: name 唯一性（软删除感知：只在 deleted=0 的记录中检查）
        checkNameUnique(req.getName(), null);

        // Step 2: 跨模块校验 modelConfigId
        //   通过 ModelConfigService（hify-model 的 api/ 接口）查询，
        //   不能直接 import model 模块的 Mapper 或 domain 类（违反 CLAUDE.md 跨模块规范）。
        //   注：ProviderService 管理供应商本身，ModelConfigService 管理具体模型配置，
        //   验证 modelConfigId 应走 ModelConfigService，语义更精确。
        ModelConfigResp modelConfig = requireEnabledModelConfig(req.getModelConfigId());
        if (req.getSummaryModelConfigId() != null) {
            requireEnabledModelConfig(req.getSummaryModelConfigId());
        }

        // Step 3: 事务内执行 INSERT agent + 批量 INSERT agent_tool
        AgentPo agentPo = buildAgentPo(req);
        agentMapper.insert(agentPo);

        List<Long> toolIds = normalizeToolIds(req.getToolIds());
        validateToolIds(toolIds);
        batchInsertTools(agentPo.getId(), toolIds);

        log.info("Agent created: id={}, name={}, tools={}", agentPo.getId(), agentPo.getName(), toolIds.size());

        // Step 4: 返回 AgentDetailResp（@Caching 注解已在方法返回后触发缓存清除）
        return buildDetailResp(agentPo, modelConfig, toolIds);
    }

    // ── 更新 ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", allEntries = true),
    })
    public AgentDetailResp update(Long id, UpdateAgentReq req) {
        AgentPo po = requireAgent(id);

        if (req.getModelConfigId() != null) {
            requireEnabledModelConfig(req.getModelConfigId());
            po.setModelConfigId(req.getModelConfigId());
        }
        if (req.getWorkflowId() != null || Boolean.TRUE.equals(req.getBindWorkflow())) {
            po.setWorkflowId(req.getWorkflowId());
        }
        if (StringUtils.hasText(req.getName())) {
            checkNameUnique(req.getName(), id);
            po.setName(req.getName());
        }
        if (req.getDescription()      != null) po.setDescription(req.getDescription());
        if (StringUtils.hasText(req.getSystemPrompt())) po.setSystemPrompt(req.getSystemPrompt());
        if (req.getKnowledgeBaseIds() != null) po.setKnowledgeBaseIds(req.getKnowledgeBaseIds());
        if (req.getTemperature()      != null) po.setTemperature(req.getTemperature());
        if (req.getMaxTokens()        != null) po.setMaxTokens(req.getMaxTokens());
        if (req.getMaxContextTurns()  != null) po.setMaxContextTurns(req.getMaxContextTurns());
        if (req.getMemoryEnabled()    != null) po.setMemoryEnabled(req.getMemoryEnabled());
        if (req.getSummaryTriggerMessageCount() != null) {
            po.setSummaryTriggerMessageCount(req.getSummaryTriggerMessageCount());
        }
        if (req.getSummaryMaxTokens() != null) po.setSummaryMaxTokens(req.getSummaryMaxTokens());
        if (req.getSummaryModelConfigId() != null) {
            requireEnabledModelConfig(req.getSummaryModelConfigId());
            po.setSummaryModelConfigId(req.getSummaryModelConfigId());
        }

        agentMapper.updateById(po);

        // toolIds != null 时替换工具绑定（null=不修改，空列表=清空）
        if (req.getToolIds() != null) {
            replaceTools(id, req.getToolIds());
        }

        ModelConfigResp modelConfig = modelConfigService.getById(po.getModelConfigId());
        List<Long> toolIds = req.getToolIds() != null ? req.getToolIds() : queryToolIds(id);
        return buildDetailResp(po, modelConfig, toolIds);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", key = "#id"),
    })
    public AgentDetailResp bindTools(Long id, List<Long> toolIds) {
        AgentPo po = requireAgent(id);
        List<Long> normalizedToolIds = replaceTools(id, toolIds);
        ModelConfigResp modelConfig = modelConfigService.getById(po.getModelConfigId());
        return buildDetailResp(po, modelConfig, normalizedToolIds);
    }

    // ── 删除 ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", allEntries = true),
    })
    public void delete(Long id) {
        requireAgent(id);
        // 先删关联工具（硬删除），再软删 Agent 本体
        agentToolMapper.delete(
                new LambdaQueryWrapper<AgentToolPo>().eq(AgentToolPo::getAgentId, id));
        agentMapper.deleteById(id);
        log.info("Agent deleted: id={}", id);
    }

    // ── 启用 / 禁用 ───────────────────────────────────────────────────

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", key = "#id"),
    })
    public AgentDetailResp toggleEnabled(Long id, int enabled) {
        AgentPo po = requireAgent(id);
        po.setEnabled(enabled);
        agentMapper.updateById(po);

        ModelConfigResp modelConfig = modelConfigService.getById(po.getModelConfigId());
        List<Long> toolIds = queryToolIds(id);
        return buildDetailResp(po, modelConfig, toolIds);
    }

    // ── 详情 ──────────────────────────────────────────────────────────

    @Override
    @Cacheable(cacheNames = "agent:detail", key = "#id")
    public AgentDetailResp getDetail(Long id) {
        AgentPo po = requireAgent(id);
        ModelConfigResp modelConfig = modelConfigService.getById(po.getModelConfigId());
        List<Long> toolIds = queryToolIds(id);
        return buildDetailResp(po, modelConfig, toolIds);
    }

    // ── 列表 ──────────────────────────────────────────────────────────

    @Override
    public PageResult<AgentListItemResp> listPage(AgentQuery query) {
        LambdaQueryWrapper<AgentPo> wrapper = new LambdaQueryWrapper<AgentPo>()
                .like(StringUtils.hasText(query.getName()), AgentPo::getName, query.getName())
                .eq(query.getEnabled() != null, AgentPo::getEnabled, query.getEnabled())
                .orderByDesc(AgentPo::getCreatedAt);

        Page<AgentPo> page = agentMapper.selectPage(
                new Page<>(query.getPage(), query.getPageSize()), wrapper);

        List<Long> agentIds = page.getRecords().stream()
                .map(AgentPo::getId).collect(Collectors.toList());

        List<Long> modelConfigIds = page.getRecords().stream()
                .map(AgentPo::getModelConfigId).distinct().collect(Collectors.toList());
        Map<Long, ModelConfigResp> modelConfigMap = modelConfigService.mapByIds(modelConfigIds);

        Map<Long, Long> toolCountMap = CollectionUtils.isEmpty(agentIds)
                ? Collections.emptyMap()
                : agentToolMapper.selectList(
                        new LambdaQueryWrapper<AgentToolPo>().in(AgentToolPo::getAgentId, agentIds))
                  .stream()
                  .collect(Collectors.groupingBy(AgentToolPo::getAgentId, Collectors.counting()));

        List<AgentListItemResp> records = page.getRecords().stream()
                .map(po -> toListItem(po, modelConfigMap.get(po.getModelConfigId()),
                        toolCountMap.getOrDefault(po.getId(), 0L).intValue()))
                .collect(Collectors.toList());

        return PageResult.ok(records, page.getTotal(), query.getPage(), query.getPageSize());
    }

    // ── private helpers ───────────────────────────────────────────────

    /**
     * 软删除感知的 name 唯一性校验。
     * 只在 deleted=0 的记录中检查，deleted=1 的同名记录不阻止新增。
     * @param excludeId 更新时传入自身 ID 排除自身；新增时传 null
     */
    private void checkNameUnique(String name, Long excludeId) {
        long count = agentMapper.selectCount(
                new LambdaQueryWrapper<AgentPo>()
                        .eq(AgentPo::getName, name)
                        .ne(excludeId != null, AgentPo::getId, excludeId));
        // MyBatis-Plus @TableLogic 已自动追加 AND deleted=0，无需手写
        if (count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "Agent 名称「" + name + "」已存在");
        }
    }

    /**
     * 跨模块校验 modelConfigId：通过 ModelConfigService（model 模块的 api/ 接口）。
     * 不可直接 import model 模块的 ModelConfigMapper 或 ModelConfigPo。
     */
    private ModelConfigResp requireEnabledModelConfig(Long modelConfigId) {
        ModelConfigResp config = modelConfigService.getById(modelConfigId);
        if (config == null || config.getEnabled() != 1) {
            throw new BizException(ErrorCode.AGENT_MODEL_UNAVAILABLE,
                    "模型配置 ID=" + modelConfigId + " 不存在或已禁用");
        }
        return config;
    }

    private AgentPo requireAgent(Long id) {
        AgentPo po = agentMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent ID=" + id + " 不存在");
        }
        return po;
    }

    /**
     * 批量插入工具关联记录。工具数量最多 10 个，逐条 INSERT 在事务内可接受。
     */
    private void batchInsertTools(Long agentId, List<Long> toolIds) {
        if (CollectionUtils.isEmpty(toolIds)) return;
        toolIds.forEach(toolId -> {
            AgentToolPo tool = new AgentToolPo();
            tool.setAgentId(agentId);
            tool.setToolId(toolId);
            agentToolMapper.insert(tool);
        });
    }

    private List<Long> replaceTools(Long agentId, List<Long> toolIds) {
        List<Long> normalizedToolIds = normalizeToolIds(toolIds);
        validateToolIds(normalizedToolIds);
        agentToolMapper.delete(new LambdaQueryWrapper<AgentToolPo>().eq(AgentToolPo::getAgentId, agentId));
        batchInsertTools(agentId, normalizedToolIds);
        return normalizedToolIds;
    }

    private List<Long> normalizeToolIds(List<Long> toolIds) {
        if (CollectionUtils.isEmpty(toolIds)) {
            return Collections.emptyList();
        }
        return toolIds.stream()
                .filter(id -> id != null)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    private void validateToolIds(List<Long> toolIds) {
        if (toolIds.size() > MAX_BOUND_TOOL_COUNT) {
            throw new BizException(ErrorCode.PARAM_ERROR, "一个 Agent 最多绑定 10 个工具");
        }
        mcpService.validateEnabledToolIds(toolIds);
    }

    private List<Long> queryToolIds(Long agentId) {
        return agentToolMapper.selectList(
                new LambdaQueryWrapper<AgentToolPo>()
                        .eq(AgentToolPo::getAgentId, agentId)
                        .orderByAsc(AgentToolPo::getId))
                .stream()
                .map(AgentToolPo::getToolId)
                .collect(Collectors.toList());
    }

    private static AgentPo buildAgentPo(CreateAgentReq req) {
        AgentPo po = new AgentPo();
        po.setName(req.getName());
        po.setDescription(req.getDescription());
        po.setSystemPrompt(req.getSystemPrompt());
        po.setModelConfigId(req.getModelConfigId());
        po.setWorkflowId(req.getWorkflowId());
        po.setKnowledgeBaseIds(
                req.getKnowledgeBaseIds() != null ? req.getKnowledgeBaseIds() : Collections.emptyList());
        po.setTemperature(req.getTemperature());
        po.setMaxTokens(req.getMaxTokens());
        po.setMaxContextTurns(req.getMaxContextTurns());
        po.setMemoryEnabled(req.getMemoryEnabled() == null ? 0 : req.getMemoryEnabled());
        po.setSummaryTriggerMessageCount(req.getSummaryTriggerMessageCount() == null
                ? 20 : req.getSummaryTriggerMessageCount());
        po.setSummaryMaxTokens(req.getSummaryMaxTokens() == null ? 800 : req.getSummaryMaxTokens());
        po.setSummaryModelConfigId(req.getSummaryModelConfigId());
        po.setEnabled(1);
        return po;
    }

    private static AgentDetailResp buildDetailResp(AgentPo po,
                                                    ModelConfigResp modelConfig,
                                                    List<Long> toolIds) {
        AgentDetailResp resp = new AgentDetailResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setSystemPrompt(po.getSystemPrompt());
        resp.setModelConfigId(po.getModelConfigId());
        resp.setModelConfig(modelConfig);
        resp.setWorkflowId(po.getWorkflowId());
        resp.setKnowledgeBaseIds(po.getKnowledgeBaseIds());
        resp.setTemperature(po.getTemperature());
        resp.setMaxTokens(po.getMaxTokens());
        resp.setMaxContextTurns(po.getMaxContextTurns());
        resp.setMemoryEnabled(po.getMemoryEnabled());
        resp.setSummaryTriggerMessageCount(po.getSummaryTriggerMessageCount());
        resp.setSummaryMaxTokens(po.getSummaryMaxTokens());
        resp.setSummaryModelConfigId(po.getSummaryModelConfigId());
        resp.setToolIds(toolIds);
        resp.setEnabled(po.getEnabled());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private static AgentListItemResp toListItem(AgentPo po, ModelConfigResp modelConfig, int toolCount) {
        AgentListItemResp item = new AgentListItemResp();
        item.setId(po.getId());
        item.setName(po.getName());
        item.setDescription(po.getDescription());
        item.setModelConfigId(po.getModelConfigId());
        item.setModelName(modelConfig != null ? modelConfig.getName() : null);
        item.setModelId(modelConfig != null ? modelConfig.getModelId() : null);
        item.setWorkflowId(po.getWorkflowId());
        item.setKnowledgeBaseIds(po.getKnowledgeBaseIds());
        item.setTemperature(po.getTemperature());
        item.setMemoryEnabled(po.getMemoryEnabled());
        item.setToolCount(toolCount);
        item.setEnabled(po.getEnabled());
        item.setCreatedAt(po.getCreatedAt());
        item.setUpdatedAt(po.getUpdatedAt());
        return item;
    }
}
