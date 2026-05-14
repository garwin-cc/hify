package com.hify.auth.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hify.common.audit.AuditLogRecord;
import com.hify.common.audit.AuditLogService;
import com.hify.auth.api.CreateIdentityProviderReq;
import com.hify.auth.api.CurrentUser;
import com.hify.auth.api.IdentityProviderResp;
import com.hify.auth.api.IdentityProviderService;
import com.hify.auth.api.UpdateIdentityProviderReq;
import com.hify.auth.infra.IdentityProviderMapper;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.log.TraceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IdentityProviderServiceImpl implements IdentityProviderService {

    private final IdentityProviderMapper identityProviderMapper;
    private final AuditLogService auditLogService;

    @Override
    public List<IdentityProviderResp> list() {
        return identityProviderMapper.selectList(Wrappers.lambdaQuery(IdentityProviderPo.class)
                        .orderByDesc(IdentityProviderPo::getCreatedAt)
                        .orderByDesc(IdentityProviderPo::getId))
                .stream()
                .map(IdentityProviderServiceImpl::toResp)
                .toList();
    }

    @Override
    @Transactional
    public IdentityProviderResp create(CreateIdentityProviderReq req) {
        checkNameUnique(req.getName().trim(), null);
        IdentityProviderPo po = new IdentityProviderPo();
        po.setName(req.getName().trim());
        po.setType(req.getType().trim().toUpperCase());
        po.setIssuerUrl(trimToEmpty(req.getIssuerUrl()));
        po.setClientId(trimToEmpty(req.getClientId()));
        po.setClientSecret(trimToEmpty(req.getClientSecret()));
        po.setLdapUrl(trimToEmpty(req.getLdapUrl()));
        po.setLdapBaseDn(trimToEmpty(req.getLdapBaseDn()));
        po.setEnabled(req.getEnabled() == null ? 0 : normalizeEnabled(req.getEnabled()));
        po.setConfigJson(StringUtils.hasText(req.getConfigJson()) ? req.getConfigJson() : "{}");
        identityProviderMapper.insert(po);
        recordAudit("IDENTITY_PROVIDER_CREATE", po, null, identityProviderAudit(po), true, null);
        return toResp(po);
    }

    @Override
    @Transactional
    public IdentityProviderResp update(Long id, UpdateIdentityProviderReq req) {
        IdentityProviderPo po = findOrThrow(id);
        Map<String, Object> before = identityProviderAudit(po);
        if (StringUtils.hasText(req.getName()) && !req.getName().trim().equals(po.getName())) {
            checkNameUnique(req.getName().trim(), id);
            po.setName(req.getName().trim());
        }
        if (StringUtils.hasText(req.getType())) {
            po.setType(req.getType().trim().toUpperCase());
        }
        if (req.getIssuerUrl() != null) {
            po.setIssuerUrl(trimToEmpty(req.getIssuerUrl()));
        }
        if (req.getClientId() != null) {
            po.setClientId(trimToEmpty(req.getClientId()));
        }
        if (StringUtils.hasText(req.getClientSecret())) {
            po.setClientSecret(req.getClientSecret().trim());
        }
        if (req.getLdapUrl() != null) {
            po.setLdapUrl(trimToEmpty(req.getLdapUrl()));
        }
        if (req.getLdapBaseDn() != null) {
            po.setLdapBaseDn(trimToEmpty(req.getLdapBaseDn()));
        }
        if (req.getEnabled() != null) {
            po.setEnabled(normalizeEnabled(req.getEnabled()));
        }
        if (req.getConfigJson() != null) {
            po.setConfigJson(StringUtils.hasText(req.getConfigJson()) ? req.getConfigJson() : "{}");
        }
        identityProviderMapper.updateById(po);
        recordAudit("IDENTITY_PROVIDER_UPDATE", po, before, identityProviderAudit(po), true, null);
        return toResp(po);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        IdentityProviderPo po = findOrThrow(id);
        Map<String, Object> before = identityProviderAudit(po);
        identityProviderMapper.deleteById(id);
        recordAudit("IDENTITY_PROVIDER_DELETE", po, before, Map.of(), true, null);
    }

    private IdentityProviderPo findOrThrow(Long id) {
        IdentityProviderPo po = identityProviderMapper.selectById(id);
        if (po == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "身份源不存在: " + id);
        }
        return po;
    }

    private void checkNameUnique(String name, Long excludeId) {
        Long count = identityProviderMapper.selectCount(Wrappers.lambdaQuery(IdentityProviderPo.class)
                .eq(IdentityProviderPo::getName, name)
                .ne(excludeId != null, IdentityProviderPo::getId, excludeId));
        if (count != null && count > 0) {
            throw new BizException(ErrorCode.CONFLICT, "身份源名称已存在");
        }
    }

    private static int normalizeEnabled(Integer enabled) {
        return enabled != null && enabled == 1 ? 1 : 0;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static IdentityProviderResp toResp(IdentityProviderPo po) {
        IdentityProviderResp resp = new IdentityProviderResp();
        resp.setId(po.getId());
        resp.setName(po.getName());
        resp.setType(po.getType());
        resp.setIssuerUrl(po.getIssuerUrl());
        resp.setClientId(po.getClientId());
        resp.setLdapUrl(po.getLdapUrl());
        resp.setLdapBaseDn(po.getLdapBaseDn());
        resp.setEnabled(po.getEnabled());
        resp.setConfigJson(po.getConfigJson());
        resp.setCreatedAt(po.getCreatedAt());
        resp.setUpdatedAt(po.getUpdatedAt());
        return resp;
    }

    private Map<String, Object> identityProviderAudit(IdentityProviderPo po) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", po.getId());
        value.put("name", po.getName());
        value.put("type", po.getType());
        value.put("issuerUrl", po.getIssuerUrl());
        value.put("clientId", po.getClientId());
        value.put("ldapUrl", po.getLdapUrl());
        value.put("ldapBaseDn", po.getLdapBaseDn());
        value.put("enabled", po.getEnabled());
        value.put("configJsonLength", po.getConfigJson() == null ? 0 : po.getConfigJson().length());
        return value;
    }

    private void recordAudit(String action, IdentityProviderPo provider, Map<String, Object> before,
                             Map<String, Object> after, boolean success, String errorMessage) {
        CurrentUser actor = CurrentUserContext.get();
        auditLogService.record(AuditLogRecord.builder()
                .traceId(TraceContext.ensureTraceId())
                .actorUserId(actor == null ? null : actor.getId())
                .actorUsername(actor == null ? "" : actor.getUsername())
                .action(action)
                .resourceType("IDENTITY_PROVIDER")
                .resourceId(provider == null ? null : provider.getId())
                .resourceName(provider == null ? "" : provider.getName())
                .success(success)
                .errorMessage(errorMessage)
                .before(before)
                .after(after)
                .build());
    }
}
