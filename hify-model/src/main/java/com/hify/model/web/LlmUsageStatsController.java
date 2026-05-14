package com.hify.model.web;

import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import com.hify.model.api.LlmUsageQuery;
import com.hify.model.api.LlmUsageStatsResp;
import com.hify.model.api.LlmUsageStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/llm-usage")
@RequireRole(UserRole.ADMIN)
@RequiredArgsConstructor
public class LlmUsageStatsController {

    private final LlmUsageStatsService llmUsageStatsService;

    @GetMapping
    public Result<List<LlmUsageStatsResp>> query(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long modelConfigId,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long appId,
            @RequestParam(required = false) String groupBy) {
        LlmUsageQuery query = new LlmUsageQuery();
        query.setStartAt(startAt);
        query.setEndAt(endAt);
        query.setProviderId(providerId);
        query.setModelConfigId(modelConfigId);
        query.setUserId(userId);
        query.setProjectId(projectId);
        query.setAppId(appId);
        query.setGroupBy(groupBy);
        return Result.ok(llmUsageStatsService.query(query));
    }
}
