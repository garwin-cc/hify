package com.hify.app.domain;

import java.time.LocalDateTime;

public interface OperationsAnalyticsService {
    OperationsAnalyticsOverview overview(Long projectId, LocalDateTime from, LocalDateTime to);
}
