package com.hify.app.domain;

import java.time.LocalDateTime;
import java.util.List;

public interface QualityEvaluationService {

    QualityEvaluationOverview overview(Long projectId, LocalDateTime from, LocalDateTime to);

    List<QualitySampleResp> samples(Long projectId, String reviewStatus, LocalDateTime from, LocalDateTime to);

    QualitySampleResp updateSampleStatus(Long sampleId, UpdateQualitySampleStatusReq req);
}
