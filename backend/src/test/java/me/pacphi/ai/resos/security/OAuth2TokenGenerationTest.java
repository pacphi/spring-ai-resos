package me.pacphi.ai.resos.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.pacphi.ai.resos.test.OAuth2TestHelper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for OAuth2 token generation.
 * Tests the Authorization Server's ability to issue tokens for various grant types.
 * Uses fixed port 8080 to avoid issuer-uri resolution issues.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"server.port=8080"})
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OAuth2TokenGenerationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String getTokenEndpoint() {
        return "http://localhost:8080/oauth2/token";
    }

    @Test
    void shouldIssueTokenForClientCredentials() throws Exception {
        // Given: Valid client credentials (from seed data or test config)
        String clientId = "test-mcp-server";
        String clientSecret = "test-mcp-secret";

        // When: Request token with grant_type=client_credentials
        String accessToken = OAuth2TestHelper.obtainClientCredentialsToken(
                getTokenEndpoint(),
                clientId,
                clientSecret,
                "backend.read", "backend.write"
        );

        // Then: Should receive a valid access token
        assertThat(accessToken).isNotNull().isNotEmpty();

        // And: Token should be a valid JWT (3 parts separated by dots)
        String[] parts = accessToken.split("\\.");
        assertThat(parts).hasSize(3);

        // And: Token payload should contain expected claims
        String payload = OAuth2TestHelper.decodeJwtPayload(accessToken);
        JsonNode claims = objectMapper.readTree(payload);

        assertThat(claims.has("sub")).isTrue();

        // Check for scope claim (can be either "scope" or "scp" depending on Spring version)
        String scopeClaimValue = null;
        if (claims.has("scope")) {
            JsonNode scopeNode = claims.get("scope");
            scopeClaimValue = scopeNode.isArray()
                    ? scopeNode.toString() // Array of scopes
                    : scopeNode.asText();   // Space-delimited string
        } else if (claims.has("scp")) {
            scopeClaimValue = claims.get("scp").toString();
        }

        assertThat(scopeClaimValue)
                .isNotNull()
                .contains("backend.read")
                .contains("backend.write");
    }

    @Test
    void shouldRejectInvalidClientCredentials() {
        // Given: Invalid client secret
        String clientId = "mcp-server";
        String invalidSecret = "wrong-secret";

        // When: Request token with invalid credentials
        // Then: Should throw exception (401 Unauthorized)
        assertThatThrownBy(() ->
                OAuth2TestHelper.obtainClientCredentialsToken(
                        getTokenEndpoint(),
                        clientId,
                        invalidSecret,
                        "backend.read"
                ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to obtain token");
    }

    @Test
    void shouldIncludeCorrectScopesInToken() throws Exception {
        // Given: Request with specific scopes
        String clientId = "test-mcp-server";
        String clientSecret = "test-mcp-secret";
        String requestedScope = "backend.read";

        // When: Request token with only read scope
        String accessToken = OAuth2TestHelper.obtainClientCredentialsToken(
                getTokenEndpoint(),
                clientId,
                clientSecret,
                requestedScope
        );

        // Then: Token should contain the requested scope
        String payload = OAuth2TestHelper.decodeJwtPayload(accessToken);
        JsonNode claims = objectMapper.readTree(payload);

        // Check for scope claim (can be "scope" or "scp")
        String scopeClaimValue = null;
        if (claims.has("scope")) {
            JsonNode scopeNode = claims.get("scope");
            scopeClaimValue = scopeNode.isArray()
                    ? scopeNode.toString()
                    : scopeNode.asText();
        } else if (claims.has("scp")) {
            scopeClaimValue = claims.get("scp").toString();
        }

        assertThat(scopeClaimValue).isNotNull().contains("backend.read");
    }

    @Test
    void shouldIncludeStandardJWTClaims() throws Exception {
        // Given: Valid client credentials
        String clientId = "test-mcp-server";
        String clientSecret = "test-mcp-secret";

        // When: Request token
        String accessToken = OAuth2TestHelper.obtainClientCredentialsToken(
                getTokenEndpoint(),
                clientId,
                clientSecret
        );

        // Then: Token should contain standard JWT claims
        String payload = OAuth2TestHelper.decodeJwtPayload(accessToken);
        JsonNode claims = objectMapper.readTree(payload);

        // Verify required claims
        assertThat(claims.has("iss")).isTrue();  // Issuer
        assertThat(claims.has("sub")).isTrue();  // Subject
        assertThat(claims.has("exp")).isTrue();  // Expiration
        assertThat(claims.has("iat")).isTrue();  // Issued At

        // Verify issuer matches expected pattern
        String issuer = claims.get("iss").asText();
        assertThat(issuer).contains("localhost:8080");
    }

    @Test
    void shouldRejectUnknownClient() {
        // Given: Non-existent client ID
        String unknownClientId = "non-existent-client";
        String clientSecret = "some-secret";

        // When: Request token with unknown client
        // Then: Should throw exception (401 Unauthorized)
        assertThatThrownBy(() ->
                OAuth2TestHelper.obtainClientCredentialsToken(
                        getTokenEndpoint(),
                        unknownClientId,
                        clientSecret
                ))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to obtain token");
    }
}
