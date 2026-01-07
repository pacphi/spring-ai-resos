package me.pacphi.ai.resos.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Seeds OAuth2 registered clients for dev and test profiles.
 * Runs after main DataSeeder to ensure user/authority tables are populated.
 */
@Component
@Profile({"dev", "test"})  // Run in dev and test profiles
@Order(100)  // Run after DataSeeder
public class OAuth2ClientSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(OAuth2ClientSeeder.class);
    private static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository registeredClientRepository;

    @Value("${oauth2.client.mcp-server.client-id:mcp-server}")
    private String mcpServerClientId;

    @Value("${oauth2.client.mcp-server.client-secret:mcp-server-secret}")
    private String mcpServerClientSecret;

    @Value("${oauth2.client.frontend-app.client-id:frontend-app}")
    private String frontendAppClientId;

    @Value("${oauth2.client.frontend-app.redirect-base-url:http://localhost:8081}")
    private String frontendRedirectBaseUrl;

    @Value("${oauth2.client.frontend-app.post-logout-redirect-url:http://localhost:8081/}")
    private String frontendPostLogoutRedirectUrl;

    public OAuth2ClientSeeder(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository registeredClientRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public void run(String... args) {
        // Check if clients already exist
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oauth2_registered_client", Integer.class);

        if (count != null && count > 0) {
            log.info("OAuth2 clients already seeded (count: {}), skipping", count);
            return;
        }

        log.info("Seeding OAuth2 registered clients for dev environment...");

        seedMcpServerClient();
        seedMcpClientClient();
        seedFrontendAppClient();

        log.info("OAuth2 client seeding completed");
    }

    private void seedMcpServerClient() {
        RegisteredClient mcpServer = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(mcpServerClientId)
                .clientIdIssuedAt(Instant.now())
                .clientSecret(passwordEncoder.encode(mcpServerClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("backend.read")
                .scope("backend.write")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();

        insertRegisteredClient(mcpServer);
        log.info("Seeded OAuth2 client: {} (client_credentials)", mcpServerClientId);
    }

    private void seedMcpClientClient() {
        RegisteredClient mcpClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("mcp-client")
                .clientIdIssuedAt(Instant.now())
                .clientSecret(passwordEncoder.encode("mcp-client-secret"))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("mcp.read")
                .scope("mcp.write")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .build())
                .build();

        insertRegisteredClient(mcpClient);
        log.info("Seeded OAuth2 client: mcp-client (client_credentials)");
    }

    private void seedFrontendAppClient() {
        String redirectUri = frontendRedirectBaseUrl + "/login/oauth2/code/frontend-app";
        String authorizedUri = frontendRedirectBaseUrl + "/authorized";

        RegisteredClient frontendApp = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(frontendAppClientId)
                .clientIdIssuedAt(Instant.now())
                // Public client - no client secret
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(redirectUri)
                .redirectUri(authorizedUri)
                .postLogoutRedirectUri(frontendPostLogoutRedirectUrl)
                .scope("openid")
                .scope("profile")
                .scope("email")
                .scope("chat.read")
                .scope("chat.write")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)  // Skip consent for dev
                        .requireProofKey(true)  // PKCE required for public clients
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(Duration.ofHours(1))
                        .refreshTokenTimeToLive(Duration.ofDays(7))
                        .reuseRefreshTokens(false)
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .build();

        insertRegisteredClient(frontendApp);
        log.info("Seeded OAuth2 client: {} (authorization_code + PKCE)", frontendAppClientId);
    }

    private void insertRegisteredClient(RegisteredClient client) {
        // Use the repository's save method which handles JSON serialization correctly
        registeredClientRepository.save(client);
    }
}
