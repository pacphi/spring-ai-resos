package me.pacphi.ai.resos.mcp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for MCP endpoint security.
 * Tests that MCP endpoints require valid JWT tokens and actuator endpoints are public.
 */
@SpringBootTest
@ActiveProfiles("test")
class McpEndpointSecurityTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /**
     * Test configuration that provides a mock JwtDecoder.
     * This avoids the need to run the backend authorization server during tests.
     */
    @TestConfiguration
    static class TestJwtDecoderConfig {
        @Bean
        @Primary
        public JwtDecoder jwtDecoder() {
            // Return a mock decoder that always returns a valid JWT for testing
            return token -> {
                return Jwt.withTokenValue(token)
                        .header("alg", "RS256")
                        .claim("sub", "test-client")
                        .claim("scope", "mcp.read mcp.write")
                        .claim("iss", "http://localhost:8080")
                        .claim("exp", Instant.now().plusSeconds(3600))
                        .claim("iat", Instant.now())
                        .build();
            };
        }
    }

    @Test
    void shouldDenyAccessToMcpEndpointWithoutToken() throws Exception {
        // When: GET /mcp/** without Authorization header
        // Then: Returns 401 Unauthorized
        mockMvc.perform(get("/mcp/tools"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowAccessToActuatorWithoutToken() throws Exception {
        // When: GET /actuator/health without token
        // Then: Returns 200 OK (actuator endpoints are public)
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldAllowAccessWithValidJWT() throws Exception {
        // Given: Valid JWT with appropriate scopes
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "mcp-client")
                .claim("scope", "mcp.read mcp.write")
                .claim("iss", "http://localhost:8080")
                .claim("exp", Instant.now().plusSeconds(3600))
                .claim("iat", Instant.now())
                .build();

        // When: GET /mcp/** with Bearer token
        // Then: Authentication succeeds (may return 404 if endpoint doesn't exist, but auth passes)
        mockMvc.perform(get("/mcp/test")
                        .with(jwt().jwt(jwt)))
                .andExpect(status().isNotFound()); // 404 means auth passed, but endpoint not found
    }

    @Test
    void shouldAcceptJWTWithValidScopes() throws Exception {
        // Given: JWT with mcp.read and mcp.write scopes
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .claim("sub", "mcp-server")
                .claim("scope", "mcp.read mcp.write")
                .claim("iss", "http://localhost:8080")
                .claim("exp", Instant.now().plusSeconds(3600))
                .claim("iat", Instant.now())
                .build();

        // When: Access MCP endpoint
        // Then: Should pass authentication
        mockMvc.perform(get("/mcp/tools")
                        .with(jwt().jwt(jwt)))
                .andExpect(status().isNotFound()); // Auth succeeds, endpoint may not exist
    }

    @Test
    void shouldAllowActuatorInfoEndpoint() throws Exception {
        // When: GET /actuator/info
        // Then: Returns 200 OK (public endpoint)
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldProtectAllMcpSubpaths() throws Exception {
        // When: Access various MCP subpaths without auth
        // Then: All should return 401 Unauthorized

        mockMvc.perform(get("/mcp/tools"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/mcp/resources"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/mcp/prompts"))
                .andExpect(status().isUnauthorized());
    }
}
