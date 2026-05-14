package com.hify.common.audit;

@FunctionalInterface
public interface AuditLogWriter {

    void insert(AuditLogPo po);
}
