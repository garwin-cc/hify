package com.hify.web;

import com.hify.app.domain.QualityEvaluationOverview;
import com.hify.app.domain.QualityEvaluationService;
import com.hify.app.domain.QualitySampleResp;
import com.hify.app.domain.UpdateQualitySampleStatusReq;
import com.hify.auth.api.RequireRole;
import com.hify.auth.api.UserRole;
import com.hify.common.web.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ops/quality")
@RequireRole(UserRole.ADMIN)
@RequiredArgsConstructor
public class QualityEvaluationController {

    private final QualityEvaluationService qualityEvaluationService;

    @GetMapping("/overview")
    public Result<QualityEvaluationOverview> overview(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.ok(qualityEvaluationService.overview(projectId, from, to));
    }

    @GetMapping("/samples")
    public Result<List<QualitySampleResp>> samples(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String reviewStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return Result.ok(qualityEvaluationService.samples(projectId, reviewStatus, from, to));
    }

    @PutMapping("/samples/{sampleId}/status")
    public Result<QualitySampleResp> updateStatus(@PathVariable Long sampleId,
                                                  @Valid @RequestBody UpdateQualitySampleStatusReq req) {
        return Result.ok(qualityEvaluationService.updateSampleStatus(sampleId, req));
    }
}
