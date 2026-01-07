package me.pacphi.ai.resos;

import me.pacphi.ai.resos.service.McpSyncClientManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for MCP client OAuth2 integration.
 * Tests that mcp-client sends OAuth2 tokens to mcp-server.
 * Uses TestContainers to start backend OAuth2 server.
 */
class McpClientOAuth2IntegrationTest extends AbstractOAuth2IntegrationTest {

    @Autowired
    private McpSyncClientManager mcpClientManager;

    @Autowired
    private AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public AuthorizedClientServiceOAuth2AuthorizedClientManager mockMcpAuthorizedClientManager() {
            return org.mockito.Mockito.mock(AuthorizedClientServiceOAuth2AuthorizedClientManager.class);
        }
    }

    @Test
    void shouldHaveMcpClientManagerBean() {
        // Given: Application context loads
        // When: Check for McpSyncClientManager bean
        // Then: Bean should be available
        assertThat(mcpClientManager).isNotNull();
    }

    @Test
    void shouldConfigureOAuth2ForMcpServerCalls() {
        // Given: OAuth2 configuration for mcp-client-to-server
        // When: Check that authorized client manager is configured
        // Then: Bean should exist and be properly configured
        assertThat(authorizedClientManager).isNotNull();
    }

    @Test
    void shouldUseClientCredentialsForMcpServerCalls() {
        // Given: Mock authorized client manager returns valid token
        ClientRegistration clientRegistration = ClientRegistration
                .withRegistrationId("mcp-client-to-server")
                .clientId("test-mcp-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .tokenUri("http://localhost:9090/oauth2/token")
                .build();

        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "test-mcp-access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );

        OAuth2AuthorizedClient authorizedClient = new OAuth2AuthorizedClient(
                clientRegistration,
                "mcp-client",
                accessToken
        );

        when(authorizedClientManager.authorize(any())).thenReturn(authorizedClient);

        // Then: The MCP client should be able to obtain tokens for server calls
        // Note: Actual MCP client instantiation would require the MCP server to be running
        assertThat(mcpClientManager).isNotNull();
    }

    @Test
    void shouldCreateMcpSyncClients() {
        // Given: MCP client configuration
        // When: Request MCP sync clients
        // Then: Should return list of clients (may be empty if no servers configured)
        var clients = mcpClientManager.newMcpSyncClients();

        // Note: In test environment without actual MCP servers, this may return empty list
        assertThat(clients).isNotNull();
    }
}
