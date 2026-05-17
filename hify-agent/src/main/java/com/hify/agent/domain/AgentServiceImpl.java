package com.hify.agent.domain;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.agent.api.*;
import com.hify.agent.infra.AgentMapper;
import com.hify.agent.infra.AgentPo;
import com.hify.agent.infra.AgentApiKeyMapper;
import com.hify.agent.infra.AgentAppMapper;
import com.hify.agent.infra.AgentToolMapper;
import com.hify.agent.infra.AgentToolPo;
import com.hify.agent.infra.AgentVersionMapper;
import com.hify.auth.api.*;
import com.hify.common.audit.AuditLogRecord;
import com.hify.common.audit.AuditLogService;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import com.hify.common.web.PageResult;
import com.hify.knowledge.api.KnowledgeBaseResp;
import com.hify.knowledge.api.KnowledgeService;
import com.hify.model.api.ModelConfigResp;
import com.hify.model.api.ModelConfigService;
import com.hify.mcp.api.McpService;
import com.hify.mcp.api.McpToolResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final AgentMapper       agentMapper;
    private final AgentToolMapper   agentToolMapper;
    private final AgentVersionMapper agentVersionMapper;
    private final AgentAppMapper agentAppMapper;
    private final AgentApiKeyMapper agentApiKeyMapper;
    // ModelConfigService 是 hify-model 的 api/ 接口，跨模块调用规范：只能通过 api/ 层
    private final ModelConfigService modelConfigService;
    private final McpService         mcpService;
    private final KnowledgeService    knowledgeService;
    private AgentWorkflowResourceService workflowResourceService;
    private AuthService authService;
    private PermissionService permissionService;
    private AuditLogService auditLogService;

    private static final int MAX_BOUND_TOOL_COUNT = 10;
    private static final int DEFAULT_MAX_TOOL_ROUNDS = 2;
    private static final int MAX_TOOL_ROUNDS = 5;

    @Autowired(required = false)
    public void setWorkflowResourceService(AgentWorkflowResourceService workflowResourceService) {
        this.workflowResourceService = workflowResourceService;
    }

    @Autowired(required = false)
    public void setAuthService(AuthService authService) {
        this.authService = authService;
    }

    @Autowired(required = false)
    public void setPermissionService(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @Autowired(required = false)
    public void setAuditLogService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

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
        ensureCanManageAgentProject(agentPo.getProjectId());
        agentMapper.insert(agentPo);

        List<Long> toolIds = normalizeToolIds(req.getToolIds());
        validateResourceBindings(agentPo, agentPo.getKnowledgeBaseIds(), agentPo.getWorkflowId(), toolIds);
        batchInsertTools(agentPo.getId(), toolIds);
        AgentVersionPo draft = createVersion(agentPo, toolIds, "DRAFT", null);
        agentPo.setDraftVersionNo(draft.getVersionNo());
        agentMapper.updateById(agentPo);

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
        if (req.getWorkspaceId() != null) po.setWorkspaceId(req.getWorkspaceId());
        if (req.getProjectId() != null) po.setProjectId(req.getProjectId());
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
        if (req.getMaxToolRounds() != null) {
            po.setMaxToolRounds(normalizeMaxToolRounds(req.getMaxToolRounds()));
        }

        List<Long> nextToolIds = req.getToolIds() != null ? normalizeToolIds(req.getToolIds()) : queryToolIds(id);
        ensureCanManageAgentProject(po.getProjectId());
        validateResourceBindings(po, po.getKnowledgeBaseIds(), po.getWorkflowId(), nextToolIds);

        agentMapper.updateById(po);

        // toolIds != null 时替换工具绑定（null=不修改，空列表=清空）
        if (req.getToolIds() != null) {
            replaceTools(id, nextToolIds);
        }
        AgentVersionPo draft = createVersion(po, nextToolIds, "DRAFT", null);
        po.setDraftVersionNo(draft.getVersionNo());
        po.setPublishStatus("DRAFT");
        agentMapper.updateById(po);

        ModelConfigResp modelConfig = modelConfigService.getById(po.getModelConfigId());
        return buildDetailResp(po, modelConfig, nextToolIds);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", key = "#id"),
    })
    public AgentDetailResp bindTools(Long id, List<Long> toolIds) {
        AgentPo po = requireAgent(id);
        List<Long> normalizedToolIds = normalizeToolIds(toolIds);
        ensureCanManageAgentProject(po.getProjectId());
        validateResourceBindings(po, po.getKnowledgeBaseIds(), po.getWorkflowId(), normalizedToolIds);
        replaceTools(id, normalizedToolIds);
        AgentVersionPo draft = createVersion(po, normalizedToolIds, "DRAFT", null);
        po.setDraftVersionNo(draft.getVersionNo());
        po.setPublishStatus("DRAFT");
        agentMapper.updateById(po);
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
        AgentPo po = requireAgent(id);
        ensureCanManageAgentProject(po.getProjectId());
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
        ensureCanManageAgentProject(po.getProjectId());
        po.setEnabled(enabled);
        agentMapper.updateById(po);

        ModelConfigResp modelConfig = modelConfigService.getById(po.getModelConfigId());
        List<Long> toolIds = queryToolIds(id);
        return buildDetailResp(po, modelConfig, toolIds);
    }

    // ── 详情 ──────────────────────────────────────────────────────────

    @Override
    public AgentDetailResp getDetail(Long id) {
        AgentPo po = requireAgent(id);
        ensureCanReadAgentProject(po.getProjectId());
        ModelConfigResp modelConfig = modelConfigService.getById(po.getModelConfigId());
        List<Long> toolIds = queryToolIds(id);
        return buildDetailResp(po, modelConfig, toolIds);
    }

    // ── 列表 ──────────────────────────────────────────────────────────

    @Override
    public PageResult<AgentListItemResp> listPage(AgentQuery query) {
        if (query.getProjectId() != null) {
            ensureCanReadAgentProject(query.getProjectId());
        }
        LambdaQueryWrapper<AgentPo> wrapper = new LambdaQueryWrapper<AgentPo>()
                .like(StringUtils.hasText(query.getName()), AgentPo::getName, query.getName())
                .eq(query.getEnabled() != null, AgentPo::getEnabled, query.getEnabled())
                .eq(query.getProjectId() != null, AgentPo::getProjectId, query.getProjectId())
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

    @Override
    public List<AgentVersionResp> listVersions(Long id) {
        AgentPo po = requireAgent(id);
        ensureCanReadAgentProject(po.getProjectId());
        return agentVersionMapper.selectList(new LambdaQueryWrapper<AgentVersionPo>()
                        .eq(AgentVersionPo::getAgentId, id)
                        .orderByDesc(AgentVersionPo::getVersionNo))
                .stream()
                .map(AgentServiceImpl::toVersionResp)
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", key = "#id"),
    })
    public AgentVersionResp publishTestVersion(Long id, AgentPublishReq req) {
        AgentPo po = requireAgent(id);
        ensureCanManageAgentProject(po.getProjectId());
        AgentVersionPo version = createVersion(po, queryToolIds(id), "TEST", LocalDateTime.now());
        po.setPublishStatus("TEST");
        po.setDraftVersionNo(version.getVersionNo());
        agentMapper.updateById(po);
        recordAudit("AGENT_PUBLISH_TEST", po, Map.of(), versionAudit(version), true, null);
        return toVersionResp(version);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", key = "#id"),
    })
    public AgentVersionResp publishVersion(Long id, AgentPublishReq req) {
        AgentPo po = requireAgent(id);
        ensureCanManageAgentProject(po.getProjectId());
        AgentVersionPo version = createVersion(po, queryToolIds(id), "PUBLISHED", LocalDateTime.now());
        po.setPublishedVersionId(version.getId());
        po.setPublishStatus("PUBLISHED");
        po.setDraftVersionNo(version.getVersionNo());
        agentMapper.updateById(po);
        recordAudit("AGENT_PUBLISH", po, Map.of(), versionAudit(version), true, null);
        return toVersionResp(version);
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = "agent:list",   allEntries = true),
            @CacheEvict(cacheNames = "agent:detail", key = "#id"),
    })
    public AgentDetailResp rollbackVersion(Long id, Integer versionNo, AgentRollbackReq req) {
        AgentPo po = requireAgent(id);
        ensureCanManageAgentProject(po.getProjectId());
        AgentVersionPo target = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionPo>()
                .eq(AgentVersionPo::getAgentId, id)
                .eq(AgentVersionPo::getVersionNo, versionNo)
                .last("LIMIT 1"));
        if (target == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent 版本不存在: " + versionNo);
        }

        applyVersion(po, target);
        agentMapper.updateById(po);
        replaceTools(id, target.getToolIdsJson());
        AgentVersionPo draft = createVersion(po, target.getToolIdsJson(), "DRAFT", null);
        po.setDraftVersionNo(draft.getVersionNo());
        po.setPublishStatus("DRAFT");
        if (req != null && Boolean.TRUE.equals(req.getPublishAfterRollback())) {
            AgentVersionPo published = createVersion(po, target.getToolIdsJson(), "PUBLISHED", LocalDateTime.now());
            po.setPublishedVersionId(published.getId());
            po.setPublishStatus("PUBLISHED");
            po.setDraftVersionNo(published.getVersionNo());
        }
        agentMapper.updateById(po);
        recordAudit("AGENT_ROLLBACK", po, versionAudit(target), agentAudit(po), true, null);
        return buildDetailResp(po, modelConfigService.getById(po.getModelConfigId()), queryToolIds(id));
    }

    @Override
    @Transactional
    public AgentAppResp createApp(Long agentId, AgentAppReq req) {
        AgentPo agent = requireAgent(agentId);
        ensureCanManageAgentProject(agent.getProjectId());
        AgentVersionPo version = agentVersionMapper.selectById(req.getPublishedVersionId());
        if (version == null || !agentId.equals(version.getAgentId()) || !"PUBLISHED".equals(version.getStatus())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "应用发布只能绑定已发布 Agent 版本");
        }
        AgentAppPo po = new AgentAppPo();
        po.setAgentId(agentId);
        po.setPublishedVersionId(version.getId());
        po.setName(req.getName());
        po.setDescription(req.getDescription() == null ? "" : req.getDescription());
        po.setWebEnabled(req.getWebEnabled() == null ? 1 : normalizeEnabled(req.getWebEnabled()));
        po.setApiEnabled(req.getApiEnabled() == null ? 0 : normalizeEnabled(req.getApiEnabled()));
        po.setEndpointPath(normalizeEndpointPath(req.getEndpointPath(), agentId));
        po.setStatus("ACTIVE");
        agentAppMapper.insert(po);
        recordAudit("AGENT_APP_PUBLISH", agent, Map.of(), appAudit(po), true, null);
        return toAppResp(po);
    }

    @Override
    public List<AgentAppResp> listApps(Long agentId) {
        AgentPo agent = requireAgent(agentId);
        ensureCanReadAgentProject(agent.getProjectId());
        return agentAppMapper.selectList(new LambdaQueryWrapper<AgentAppPo>()
                        .eq(AgentAppPo::getAgentId, agentId)
                        .orderByDesc(AgentAppPo::getCreatedAt))
                .stream()
                .map(AgentServiceImpl::toAppResp)
                .toList();
    }

    @Override
    @Transactional
    public AgentApiKeyCreateResp createApiKey(Long appId, AgentApiKeyReq req) {
        AgentAppPo app = requireApp(appId);
        AgentPo agent = requireAgent(app.getAgentId());
        ensureCanManageAgentProject(agent.getProjectId());
        String apiKey = newApiKey();
        AgentApiKeyPo po = new AgentApiKeyPo();
        po.setAgentAppId(appId);
        po.setName(req.getName());
        po.setKeyPrefix(apiKey.substring(0, 16));
        po.setKeyHash(sha256(apiKey));
        po.setStatus("ACTIVE");
        agentApiKeyMapper.insert(po);
        recordAudit("AGENT_API_KEY_CREATE", agent, Map.of(), apiKeyAudit(po), true, null);
        AgentApiKeyCreateResp resp = new AgentApiKeyCreateResp();
        fillApiKeyResp(resp, po);
        resp.setApiKey(apiKey);
        return resp;
    }

    @Override
    public List<AgentApiKeyResp> listApiKeys(Long appId) {
        requireApp(appId);
        return agentApiKeyMapper.selectList(new LambdaQueryWrapper<AgentApiKeyPo>()
                        .eq(AgentApiKeyPo::getAgentAppId, appId)
                        .orderByDesc(AgentApiKeyPo::getCreatedAt))
                .stream()
                .map(AgentServiceImpl::toApiKeyResp)
                .toList();
    }

    @Override
    @Transactional
    public void revokeApiKey(Long appId, Long keyId) {
        AgentAppPo app = requireApp(appId);
        AgentPo agent = requireAgent(app.getAgentId());
        ensureCanManageAgentProject(agent.getProjectId());
        AgentApiKeyPo key = agentApiKeyMapper.selectById(keyId);
        if (key == null || !appId.equals(key.getAgentAppId())) {
            throw new BizException(ErrorCode.NOT_FOUND, "API Key 不存在: " + keyId);
        }
        key.setStatus("REVOKED");
        agentApiKeyMapper.updateById(key);
        recordAudit("AGENT_API_KEY_REVOKE", agent, apiKeyAudit(key), apiKeyAudit(key), true, null);
    }

    @Override
    @Transactional
    public AgentApiKeyAuthResp authenticateApiKey(String endpointPath, String apiKey) {
        if (!StringUtils.hasText(endpointPath) || !StringUtils.hasText(apiKey)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "API Key 认证失败");
        }
        AgentAppPo app = agentAppMapper.selectOne(new LambdaQueryWrapper<AgentAppPo>()
                .eq(AgentAppPo::getEndpointPath, normalizeEndpointPath(endpointPath))
                .eq(AgentAppPo::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (app == null || !Integer.valueOf(1).equals(app.getApiEnabled())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "API Key 认证失败");
        }
        AgentPo agent = requireAgent(app.getAgentId());
        if (!Integer.valueOf(1).equals(agent.getEnabled())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Agent 已禁用");
        }
        AgentApiKeyPo key = agentApiKeyMapper.selectOne(new LambdaQueryWrapper<AgentApiKeyPo>()
                .eq(AgentApiKeyPo::getAgentAppId, app.getId())
                .eq(AgentApiKeyPo::getKeyHash, sha256(apiKey))
                .eq(AgentApiKeyPo::getStatus, "ACTIVE")
                .last("LIMIT 1"));
        if (key == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "API Key 认证失败");
        }
        AgentVersionPo version = agentVersionMapper.selectById(app.getPublishedVersionId());
        if (version == null || !app.getAgentId().equals(version.getAgentId())
                || !"PUBLISHED".equals(version.getStatus())) {
            throw new BizException(ErrorCode.FORBIDDEN, "Agent App 未绑定可用发布版本");
        }
        key.setLastUsedAt(LocalDateTime.now());
        agentApiKeyMapper.updateById(key);

        AgentApiKeyAuthResp resp = new AgentApiKeyAuthResp();
        resp.setAgentId(agent.getId());
        resp.setAppId(app.getId());
        resp.setApiKeyId(key.getId());
        resp.setProjectId(agent.getProjectId());
        resp.setAgentVersionId(version.getId());
        resp.setAgentVersionNo(version.getVersionNo());
        resp.setEndpointPath(app.getEndpointPath());
        resp.setAgent(buildDetailResp(agent, version));
        return resp;
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

    private void validateResourceBindings(AgentPo agent, List<Long> knowledgeBaseIds,
                                          Long workflowId, List<Long> toolIds) {
        validateKnowledgeBindings(agent, knowledgeBaseIds);
        validateWorkflowBinding(agent, workflowId);
        validateToolBindings(agent, toolIds);
    }

    private void validateKnowledgeBindings(AgentPo agent, List<Long> knowledgeBaseIds) {
        if (CollectionUtils.isEmpty(knowledgeBaseIds)) {
            return;
        }
        for (Long knowledgeBaseId : normalizeToolIds(knowledgeBaseIds)) {
            KnowledgeBaseResp knowledgeBase = knowledgeService.getKnowledgeBase(knowledgeBaseId);
            if (knowledgeBase == null || !Integer.valueOf(1).equals(knowledgeBase.getEnabled())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "知识库不存在或未启用: " + knowledgeBaseId);
            }
            ensureResourceProjectAccess(agent.getProjectId(), knowledgeBase.getProjectId(), "知识库", knowledgeBaseId);
        }
    }

    private void validateWorkflowBinding(AgentPo agent, Long workflowId) {
        if (workflowId == null) {
            return;
        }
        if (workflowResourceService == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流资源校验服务不可用");
        }
        AgentResourceRef workflow = workflowResourceService.getWorkflowResource(workflowId);
        if (workflow == null || !Integer.valueOf(1).equals(workflow.getEnabled())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工作流不存在或未启用: " + workflowId);
        }
        ensureResourceProjectAccess(agent.getProjectId(), workflow.getProjectId(), "工作流", workflowId);
    }

    private void validateToolBindings(AgentPo agent, List<Long> toolIds) {
        validateToolIds(toolIds);
        if (CollectionUtils.isEmpty(toolIds)) {
            return;
        }
        List<McpToolResp> tools = mcpService.listEnabledToolsByIds(toolIds);
        for (McpToolResp tool : tools) {
            ensureResourceProjectAccess(agent.getProjectId(), tool.getProjectId(), "MCP 工具", tool.getId());
            if (Integer.valueOf(1).equals(tool.getDangerous())) {
                ensureCanManageTargetProject(tool.getProjectId(), "危险 MCP 工具需要目标项目管理权限: " + tool.getName());
            }
        }
    }

    private void ensureResourceProjectAccess(Long agentProjectId, Long resourceProjectId,
                                             String resourceType, Long resourceId) {
        if (resourceProjectId == null || resourceProjectId.equals(agentProjectId)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN,
                resourceType + " 必须属于同一项目，禁止跨项目绑定: " + resourceId);
    }

    private void ensureCanManageAgentProject(Long projectId) {
        ensureCanManageTargetProject(projectId, "当前用户无 Agent 项目管理权限");
    }

    private void ensureCanReadAgentProject(Long projectId) {
        CurrentUser user = currentUser();
        if (user == null || user.getRole() == UserRole.ADMIN || permissionService == null
                || permissionService.canAccessProject(user, projectId, PermissionAction.READ)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "当前用户无 Agent 项目读取权限");
    }

    private void ensureCanManageTargetProject(Long projectId, String message) {
        CurrentUser user = currentUser();
        if (user == null || user.getRole() == UserRole.ADMIN || permissionService == null
                || permissionService.canAccessProject(user, projectId, PermissionAction.MANAGE)) {
            return;
        }
        throw new BizException(ErrorCode.FORBIDDEN, message);
    }

    private CurrentUser currentUser() {
        if (authService == null) {
            return null;
        }
        try {
            return authService.getCurrentUser();
        } catch (Exception ignored) {
            return null;
        }
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

    private AgentVersionPo createVersion(AgentPo agent, List<Long> toolIds, String status, LocalDateTime publishedAt) {
        Integer versionNo = nextVersionNo(agent.getId());
        AgentVersionPo version = new AgentVersionPo();
        version.setAgentId(agent.getId());
        version.setVersionNo(versionNo);
        version.setStatus(status);
        version.setName(agent.getName());
        version.setDescription(agent.getDescription());
        version.setSystemPrompt(agent.getSystemPrompt());
        version.setModelConfigId(agent.getModelConfigId());
        version.setWorkflowId(agent.getWorkflowId());
        version.setKnowledgeBaseIdsJson(safeList(agent.getKnowledgeBaseIds()));
        version.setToolIdsJson(safeList(toolIds));
        version.setTemperature(agent.getTemperature());
        version.setMaxTokens(agent.getMaxTokens());
        version.setMaxContextTurns(agent.getMaxContextTurns());
        version.setMemoryEnabled(agent.getMemoryEnabled() == null ? 0 : agent.getMemoryEnabled());
        version.setSummaryTriggerMessageCount(agent.getSummaryTriggerMessageCount() == null
                ? 20 : agent.getSummaryTriggerMessageCount());
        version.setSummaryMaxTokens(agent.getSummaryMaxTokens() == null ? 800 : agent.getSummaryMaxTokens());
        version.setSummaryModelConfigId(agent.getSummaryModelConfigId());
        version.setMaxToolRounds(normalizeMaxToolRounds(agent.getMaxToolRounds()));
        version.setSnapshotJson(versionAudit(version));
        version.setPublishedAt(publishedAt);
        agentVersionMapper.insert(version);
        return version;
    }

    private Integer nextVersionNo(Long agentId) {
        AgentVersionPo latest = agentVersionMapper.selectOne(new LambdaQueryWrapper<AgentVersionPo>()
                .eq(AgentVersionPo::getAgentId, agentId)
                .orderByDesc(AgentVersionPo::getVersionNo)
                .last("LIMIT 1"));
        return latest == null || latest.getVersionNo() == null ? 1 : latest.getVersionNo() + 1;
    }

    private void applyVersion(AgentPo agent, AgentVersionPo version) {
        agent.setName(version.getName());
        agent.setDescription(version.getDescription());
        agent.setSystemPrompt(version.getSystemPrompt());
        agent.setModelConfigId(version.getModelConfigId());
        agent.setWorkflowId(version.getWorkflowId());
        agent.setKnowledgeBaseIds(safeList(version.getKnowledgeBaseIdsJson()));
        agent.setTemperature(version.getTemperature());
        agent.setMaxTokens(version.getMaxTokens());
        agent.setMaxContextTurns(version.getMaxContextTurns());
        agent.setMemoryEnabled(version.getMemoryEnabled());
        agent.setSummaryTriggerMessageCount(version.getSummaryTriggerMessageCount());
        agent.setSummaryMaxTokens(version.getSummaryMaxTokens());
        agent.setSummaryModelConfigId(version.getSummaryModelConfigId());
        agent.setMaxToolRounds(normalizeMaxToolRounds(version.getMaxToolRounds()));
    }

    private AgentAppPo requireApp(Long appId) {
        AgentAppPo app = agentAppMapper.selectById(appId);
        if (app == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "Agent App 不存在: " + appId);
        }
        return app;
    }

    private static List<Long> safeList(List<Long> ids) {
        return ids == null ? List.of() : List.copyOf(ids);
    }

    private static int normalizeMaxToolRounds(Integer value) {
        if (value == null) {
            return DEFAULT_MAX_TOOL_ROUNDS;
        }
        if (value < 0 || value > MAX_TOOL_ROUNDS) {
            throw new BizException(ErrorCode.PARAM_ERROR, "maxToolRounds 必须在 0-5 之间");
        }
        return value;
    }

    private static int normalizeEnabled(Integer value) {
        return value != null && value == 0 ? 0 : 1;
    }

    private static String normalizeEndpointPath(String endpointPath, Long agentId) {
        if (!StringUtils.hasText(endpointPath)) {
            return "/api/agents/" + agentId;
        }
        return normalizeEndpointPath(endpointPath);
    }

    private static String normalizeEndpointPath(String endpointPath) {
        String normalized = endpointPath.trim();
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String newApiKey() {
        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        return "hify_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "API Key 哈希生成失败");
        }
    }

    static String sha256ForTest(String value) {
        return sha256(value);
    }

    private static AgentPo buildAgentPo(CreateAgentReq req) {
        AgentPo po = new AgentPo();
        po.setWorkspaceId(req.getWorkspaceId() == null ? 1L : req.getWorkspaceId());
        po.setProjectId(req.getProjectId() == null ? 1L : req.getProjectId());
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
        po.setDraftVersionNo(1);
        po.setPublishStatus("DRAFT");
        po.setMaxToolRounds(normalizeMaxToolRounds(req.getMaxToolRounds()));
        return po;
    }

    private static AgentDetailResp buildDetailResp(AgentPo po,
                                                    ModelConfigResp modelConfig,
                                                    List<Long> toolIds) {
        AgentDetailResp resp = new AgentDetailResp();
        resp.setId(po.getId());
        resp.setWorkspaceId(po.getWorkspaceId());
        resp.setProjectId(po.getProjectId());
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
        resp.setDraftVersionNo(po.getDraftVersionNo());
        resp.setPublishedVersionId(po.getPublishedVersionId());
        resp.setPublishStatus(po.getPublishStatus());
        resp.setMaxToolRounds(normalizeMaxToolRounds(po.getMaxToolRounds()));
        resp.setEnabled(po.getEnabled());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private static AgentDetailResp buildDetailResp(AgentPo agent, AgentVersionPo version) {
        AgentDetailResp resp = new AgentDetailResp();
        resp.setId(agent.getId());
        resp.setWorkspaceId(agent.getWorkspaceId());
        resp.setProjectId(agent.getProjectId());
        resp.setName(version.getName());
        resp.setDescription(version.getDescription());
        resp.setSystemPrompt(version.getSystemPrompt());
        resp.setModelConfigId(version.getModelConfigId());
        resp.setWorkflowId(version.getWorkflowId());
        resp.setKnowledgeBaseIds(safeList(version.getKnowledgeBaseIdsJson()));
        resp.setTemperature(version.getTemperature());
        resp.setMaxTokens(version.getMaxTokens());
        resp.setMaxContextTurns(version.getMaxContextTurns());
        resp.setMemoryEnabled(version.getMemoryEnabled());
        resp.setSummaryTriggerMessageCount(version.getSummaryTriggerMessageCount());
        resp.setSummaryMaxTokens(version.getSummaryMaxTokens());
        resp.setSummaryModelConfigId(version.getSummaryModelConfigId());
        resp.setToolIds(safeList(version.getToolIdsJson()));
        resp.setDraftVersionNo(version.getVersionNo());
        resp.setPublishedVersionId(version.getId());
        resp.setPublishStatus("PUBLISHED");
        resp.setMaxToolRounds(normalizeMaxToolRounds(version.getMaxToolRounds()));
        resp.setEnabled(agent.getEnabled());
        resp.setCreatedAt(agent.getCreatedAt());
        resp.setUpdatedAt(agent.getUpdatedAt());
        return resp;
    }

    private static AgentListItemResp toListItem(AgentPo po, ModelConfigResp modelConfig, int toolCount) {
        AgentListItemResp item = new AgentListItemResp();
        item.setId(po.getId());
        item.setWorkspaceId(po.getWorkspaceId());
        item.setProjectId(po.getProjectId());
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
        item.setPublishedVersionId(po.getPublishedVersionId());
        item.setPublishStatus(po.getPublishStatus());
        item.setMaxToolRounds(normalizeMaxToolRounds(po.getMaxToolRounds()));
        item.setEnabled(po.getEnabled());
        item.setCreatedAt(po.getCreatedAt());
        item.setUpdatedAt(po.getUpdatedAt());
        return item;
    }

    private static AgentVersionResp toVersionResp(AgentVersionPo po) {
        AgentVersionResp resp = new AgentVersionResp();
        resp.setId(po.getId());
        resp.setAgentId(po.getAgentId());
        resp.setVersionNo(po.getVersionNo());
        resp.setStatus(po.getStatus());
        resp.setName(po.getName());
        resp.setModelConfigId(po.getModelConfigId());
        resp.setWorkflowId(po.getWorkflowId());
        resp.setKnowledgeBaseIds(safeList(po.getKnowledgeBaseIdsJson()));
        resp.setToolIds(safeList(po.getToolIdsJson()));
        resp.setMaxToolRounds(po.getMaxToolRounds());
        resp.setPublishedAt(po.getPublishedAt());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }

    private static AgentAppResp toAppResp(AgentAppPo po) {
        AgentAppResp resp = new AgentAppResp();
        resp.setId(po.getId());
        resp.setAgentId(po.getAgentId());
        resp.setPublishedVersionId(po.getPublishedVersionId());
        resp.setName(po.getName());
        resp.setDescription(po.getDescription());
        resp.setWebEnabled(po.getWebEnabled());
        resp.setApiEnabled(po.getApiEnabled());
        resp.setEndpointPath(po.getEndpointPath());
        resp.setStatus(po.getStatus());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private static AgentApiKeyResp toApiKeyResp(AgentApiKeyPo po) {
        AgentApiKeyResp resp = new AgentApiKeyResp();
        fillApiKeyResp(resp, po);
        return resp;
    }

    private static void fillApiKeyResp(AgentApiKeyResp resp, AgentApiKeyPo po) {
        resp.setId(po.getId());
        resp.setAgentAppId(po.getAgentAppId());
        resp.setName(po.getName());
        resp.setKeyPrefix(po.getKeyPrefix());
        resp.setStatus(po.getStatus());
        resp.setLastUsedAt(po.getLastUsedAt());
        resp.setCreatedAt(po.getCreatedAt());
    }

    private Map<String, Object> agentAudit(AgentPo po) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", po.getId());
        value.put("workspaceId", po.getWorkspaceId());
        value.put("projectId", po.getProjectId());
        value.put("name", po.getName());
        value.put("modelConfigId", po.getModelConfigId());
        value.put("workflowId", po.getWorkflowId());
        value.put("knowledgeBaseIds", safeList(po.getKnowledgeBaseIds()));
        value.put("publishedVersionId", po.getPublishedVersionId());
        value.put("publishStatus", po.getPublishStatus());
        value.put("maxToolRounds", po.getMaxToolRounds());
        return value;
    }

    private static Map<String, Object> versionAudit(AgentVersionPo po) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", po.getId());
        value.put("agentId", po.getAgentId());
        value.put("versionNo", po.getVersionNo());
        value.put("status", po.getStatus());
        value.put("name", po.getName());
        value.put("modelConfigId", po.getModelConfigId());
        value.put("workflowId", po.getWorkflowId());
        value.put("knowledgeBaseIds", safeList(po.getKnowledgeBaseIdsJson()));
        value.put("toolIds", safeList(po.getToolIdsJson()));
        value.put("maxToolRounds", po.getMaxToolRounds());
        return value;
    }

    private static Map<String, Object> appAudit(AgentAppPo po) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", po.getId());
        value.put("agentId", po.getAgentId());
        value.put("publishedVersionId", po.getPublishedVersionId());
        value.put("name", po.getName());
        value.put("endpointPath", po.getEndpointPath());
        value.put("webEnabled", po.getWebEnabled());
        value.put("apiEnabled", po.getApiEnabled());
        return value;
    }

    private static Map<String, Object> apiKeyAudit(AgentApiKeyPo po) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", po.getId());
        value.put("agentAppId", po.getAgentAppId());
        value.put("name", po.getName());
        value.put("keyPrefix", po.getKeyPrefix());
        value.put("status", po.getStatus());
        return value;
    }

    private void recordAudit(String action, AgentPo agent, Map<String, Object> before,
                             Map<String, Object> after, boolean success, String errorMessage) {
        if (auditLogService == null) {
            return;
        }
        CurrentUser user = currentUser();
        auditLogService.record(AuditLogRecord.builder()
                .traceId(TraceContext.ensureTraceId())
                .actorUserId(user == null ? null : user.getId())
                .actorUsername(user == null ? "" : user.getUsername())
                .workspaceId(agent == null ? null : agent.getWorkspaceId())
                .projectId(agent == null ? null : agent.getProjectId())
                .action(action)
                .resourceType("AGENT")
                .resourceId(agent == null ? null : agent.getId())
                .resourceName(agent == null ? "" : agent.getName())
                .success(success)
                .errorMessage(errorMessage)
                .before(before)
                .after(after)
                .build());
    }
}
