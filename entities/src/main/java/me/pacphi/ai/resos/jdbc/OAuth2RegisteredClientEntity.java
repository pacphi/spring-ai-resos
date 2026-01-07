package me.pacphi.ai.resos.jdbc;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;

/**
 * Entity for OAuth2 registered clients.
 * Schema matches Spring Authorization Server's JdbcRegisteredClientRepository expectations.
 */
@Table("oauth2_registered_client")
public class OAuth2RegisteredClientEntity {

    @Id
    private String id;

    @Column("client_id")
    private String clientId;

    @Column("client_id_issued_at")
    private Instant clientIdIssuedAt;

    @Column("client_secret")
    private String clientSecret;

    @Column("client_secret_expires_at")
    private Instant clientSecretExpiresAt;

    @Column("client_name")
    private String clientName;

    @Column("client_authentication_methods")
    private String clientAuthenticationMethods;

    @Column("authorization_grant_types")
    private String authorizationGrantTypes;

    @Column("redirect_uris")
    private String redirectUris;

    @Column("post_logout_redirect_uris")
    private String postLogoutRedirectUris;

    @Column("scopes")
    private String scopes;

    @Column("client_settings")
    private String clientSettings;

    @Column("token_settings")
    private String tokenSettings;

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public Instant getClientIdIssuedAt() { return clientIdIssuedAt; }
    public void setClientIdIssuedAt(Instant clientIdIssuedAt) { this.clientIdIssuedAt = clientIdIssuedAt; }

    public String getClientSecret() { return clientSecret; }
    public void setClientSecret(String clientSecret) { this.clientSecret = clientSecret; }

    public Instant getClientSecretExpiresAt() { return clientSecretExpiresAt; }
    public void setClientSecretExpiresAt(Instant clientSecretExpiresAt) { this.clientSecretExpiresAt = clientSecretExpiresAt; }

    public String getClientName() { return clientName; }
    public void setClientName(String clientName) { this.clientName = clientName; }

    public String getClientAuthenticationMethods() { return clientAuthenticationMethods; }
    public void setClientAuthenticationMethods(String clientAuthenticationMethods) { this.clientAuthenticationMethods = clientAuthenticationMethods; }

    public String getAuthorizationGrantTypes() { return authorizationGrantTypes; }
    public void setAuthorizationGrantTypes(String authorizationGrantTypes) { this.authorizationGrantTypes = authorizationGrantTypes; }

    public String getRedirectUris() { return redirectUris; }
    public void setRedirectUris(String redirectUris) { this.redirectUris = redirectUris; }

    public String getPostLogoutRedirectUris() { return postLogoutRedirectUris; }
    public void setPostLogoutRedirectUris(String postLogoutRedirectUris) { this.postLogoutRedirectUris = postLogoutRedirectUris; }

    public String getScopes() { return scopes; }
    public void setScopes(String scopes) { this.scopes = scopes; }

    public String getClientSettings() { return clientSettings; }
    public void setClientSettings(String clientSettings) { this.clientSettings = clientSettings; }

    public String getTokenSettings() { return tokenSettings; }
    public void setTokenSettings(String tokenSettings) { this.tokenSettings = tokenSettings; }
}
