package com.hify.web;

import com.hify.app.domain.OperationsAnalyticsOverview;
import com.hify.app.domain.OperationsAnalyticsService;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/ops/analytics")
@RequireRole(UserRole.ADMIN)
@RequiredArgsConstructor
public class OperationsAnalyticsController {

    private final OperationsAnalyticsService operationsAnalyticsService;

    @GetMapping("/overview")
    public Result<OperationsAnalyticsOverview> overview(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.ok(operationsAnalyticsService.overview(projectId, from, to));
    }
}
