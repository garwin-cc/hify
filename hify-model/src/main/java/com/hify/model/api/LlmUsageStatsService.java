package com.hify.model.api;

import java.util.List;

public interface LlmUsageStatsService {

    void record(LlmCallStatRecord record);

    List<LlmUsageStatsResp> query(LlmUsageQuery query);
}
