package com.hify.model.api;

import java.util.List;

public interface ModelDefaultPolicyService {

    List<ModelDefaultPolicyResp> list();

    ModelDefaultPolicyResp upsert(ModelDefaultPolicyReq req);

    ModelDefaultPolicyResp resolve(String modelType, LlmCallContext context, String fallbackProviderType);
}
