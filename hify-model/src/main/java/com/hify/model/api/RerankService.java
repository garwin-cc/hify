package com.hify.model.api;

import java.util.List;

public interface RerankService {

    List<RerankResult> rerank(RerankRequest req);
}
