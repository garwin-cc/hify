package com.hify.auth.api;

import java.util.List;

public interface IdentityProviderService {

    List<IdentityProviderResp> list();

    IdentityProviderResp create(CreateIdentityProviderReq req);

    IdentityProviderResp update(Long id, UpdateIdentityProviderReq req);

    void delete(Long id);
}
