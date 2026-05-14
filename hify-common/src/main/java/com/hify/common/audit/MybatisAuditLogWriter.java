package com.hify.common.audit;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MybatisAuditLogWriter implements AuditLogWriter {

    private final AuditLogMapper auditLogMapper;

    @Override
    public void insert(AuditLogPo po) {
        auditLogMapper.insert(po);
    }
}
