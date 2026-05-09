package com.hify.model.api;

import java.util.List;

public interface EmbeddingService {

    List<List<Double>> embed(Long modelConfigId, List<String> inputs);
}
