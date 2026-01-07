package me.pacphi.ai.resos.config;

import org.springaicommunity.mcp.security.client.sync.oauth2.http.client.OAuth2ClientCredentialsSyncHttpRequestCustomizer;
import org.springaicommunity.mcp.security.client.sync.AuthenticationMcpTransportContextProvider;
import org.springframework.ai.mcp.customizer.McpSyncClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

/**
 * MCP Client OAuth2 configuration using spring-ai-community/mcp-security.
 * Configures client_credentials flow for mcp-client → mcp-server calls.
 */
@Configuration
public class McpClientOAuth2Config {

    @Bean
    public McpSyncClientCustomizer mcpSyncClientCustomizer() {
        return (name, syncSpec) ->
                syncSpec.transportContextProvider(
                        new AuthenticationMcpTransportContextProvider()
                );
    }

    @Bean
    public AuthorizedClientServiceOAuth2AuthorizedClientManager mcpAuthorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        OAuth2AuthorizedClientProvider authorizedClientProvider =
                OAuth2AuthorizedClientProviderBuilder.builder()
                        .clientCredentials()
                        .build();

        AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager =
                new AuthorizedClientServiceOAuth2AuthorizedClientManager(
                        clientRegistrationRepository,
                        authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    public OAuth2ClientCredentialsSyncHttpRequestCustomizer mcpHttpRequestCustomizer(
            AuthorizedClientServiceOAuth2AuthorizedClientManager clientManager) {
        return new OAuth2ClientCredentialsSyncHttpRequestCustomizer(
                clientManager,
                "mcp-client-to-server"  // OAuth2 client registration ID
        );
    }
}
