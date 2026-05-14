package com.hify.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateIdentityProviderReq {

    @NotBlank
    @Size(max = 100)
    private String name;

    @NotBlank
    @Pattern(regexp = "OIDC|LDAP|SAML")
    private String type;

    @Size(max = 255)
    private String issuerUrl;

    @Size(max = 128)
    private String clientId;

    @Size(max = 512)
    private String clientSecret;

    @Size(max = 255)
    private String ldapUrl;

    @Size(max = 255)
    private String ldapBaseDn;

    private Integer enabled;

    private String configJson;
}
