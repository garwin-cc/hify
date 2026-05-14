package com.hify.mcp.domain;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hify.common.exception.BizException;
import com.hify.common.exception.ErrorCode;
import com.hify.common.util.PageHelper;
import com.hify.common.web.PageResult;
import com.hify.mcp.api.CreateToolSecretReq;
import com.hify.mcp.api.ToolSecretQuery;
import com.hify.mcp.api.ToolSecretResp;
import com.hify.mcp.infra.ToolSecretMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ToolSecretService {

    private static final int GCM_TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    private final ToolSecretMapper toolSecretMapper;

    @Value("${hify.tool-secret.master-key:}")
    private String masterKey;

    public ToolSecretResp create(CreateToolSecretReq req) {
        if (!StringUtils.hasText(masterKey)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未配置 hify.tool-secret.master-key，不能创建工具 Secret");
        }
        ToolSecretPo po = new ToolSecretPo();
        po.setWorkspaceId(req.getWorkspaceId() == null ? 1L : req.getWorkspaceId());
        po.setProjectId(req.getProjectId() == null ? 1L : req.getProjectId());
        po.setName(req.getName());
        po.setSecretType(req.getSecretType().trim().toUpperCase());
        po.setKeyPrefix(prefix(req.getSecretValue()));
        po.setEncryptedValue(encrypt(req.getSecretValue()));
        po.setStatus("ACTIVE");
        toolSecretMapper.insert(po);
        return toResp(po);
    }

    public PageResult<ToolSecretResp> list(ToolSecretQuery query) {
        Page<ToolSecretPo> page = PageHelper.toPage(query.getPage(), query.getSize());
        return PageHelper.toPageResult(toolSecretMapper.selectPage(page,
                Wrappers.lambdaQuery(ToolSecretPo.class)
                        .eq(query.getProjectId() != null, ToolSecretPo::getProjectId, query.getProjectId())
                        .eq(StringUtils.hasText(query.getStatus()), ToolSecretPo::getStatus, query.getStatus())
                        .orderByDesc(ToolSecretPo::getCreatedAt)), this::toResp);
    }

    public void delete(Long id) {
        toolSecretMapper.deleteById(id);
    }

    public String resolve(Long id) {
        if (id == null) {
            return "";
        }
        ToolSecretPo po = toolSecretMapper.selectById(id);
        if (po == null || !"ACTIVE".equals(po.getStatus())) {
            throw new BizException(ErrorCode.NOT_FOUND, "工具 Secret 不存在或未启用");
        }
        return decrypt(po.getEncryptedValue());
    }

    private String encrypt(String plainText) {
        try {
            byte[] iv = new byte[IV_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(iv) + "." + Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "工具 Secret 加密失败", e);
        }
    }

    private String decrypt(String encryptedValue) {
        try {
            String[] parts = encryptedValue.split("\\.", 2);
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] encrypted = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "工具 Secret 解密失败", e);
        }
    }

    private SecretKeySpec keySpec() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(masterKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }

    private String prefix(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.length() <= 6 ? value : value.substring(0, 6);
    }

    private ToolSecretResp toResp(ToolSecretPo po) {
        ToolSecretResp resp = new ToolSecretResp();
        resp.setId(po.getId());
        resp.setWorkspaceId(po.getWorkspaceId());
        resp.setProjectId(po.getProjectId());
        resp.setName(po.getName());
        resp.setSecretType(po.getSecretType());
        resp.setKeyPrefix(po.getKeyPrefix());
        resp.setStatus(po.getStatus());
        resp.setCreatedAt(po.getCreatedAt());
        return resp;
    }
}
