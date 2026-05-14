package com.hify.auth.api;

public interface AuditLogService {

    void record(AuditLogRecord record);
}
