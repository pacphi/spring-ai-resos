package me.pacphi.ai.resos.jdbc;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * Entity for OAuth2 authorization consents.
 * Schema matches Spring Authorization Server's JdbcOAuth2AuthorizationConsentService expectations.
 * This table has a composite primary key (registered_client_id, principal_name).
 */
@Table("oauth2_authorization_consent")
public class OAuth2AuthorizationConsentEntity {

    @Column("registered_client_id")
    private String registeredClientId;

    @Column("principal_name")
    private String principalName;

    @Column("authorities")
    private String authorities;

    // Getters and Setters
    public String getRegisteredClientId() { return registeredClientId; }
    public void setRegisteredClientId(String registeredClientId) { this.registeredClientId = registeredClientId; }

    public String getPrincipalName() { return principalName; }
    public void setPrincipalName(String principalName) { this.principalName = principalName; }

    public String getAuthorities() { return authorities; }
    public void setAuthorities(String authorities) { this.authorities = authorities; }
}
