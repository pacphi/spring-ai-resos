package me.pacphi.ai.resos;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for OAuth2 login configuration.
 * Tests OAuth2 client setup, auth status endpoints, and user info.
 * Uses TestContainers to start backend OAuth2 server.
 *
 * OAuth2ClientAutoConfiguration is excluded via application-test.yml to avoid issuer-uri validation.
 * ClientRegistrationRepository is provided manually by AbstractOAuth2IntegrationTest.
 */
class OAuth2LoginConfigurationTest extends AbstractOAuth2IntegrationTest {

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

    @Test
    void shouldReturnAuthStatusForUnauthenticatedUser() throws Exception {
        // When: GET /api/auth/status without any authentication
        // Then: Returns {authenticated: false, loginUrl: "..."}
        mockMvc.perform(get("/api/auth/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(false)))
                .andExpect(jsonPath("$.loginUrl").value("/oauth2/authorization/frontend-app"));
    }

    @Test
    void shouldReturnAuthStatusForAuthenticatedUser() throws Exception {
        // Given: Authenticated OAuth2 user
        // When: GET /api/auth/status
        // Then: Returns {authenticated: true, username: "..."}
        mockMvc.perform(get("/api/auth/status")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "test-user")
                                        .claim("email", "test@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated", is(true)))
                .andExpect(jsonPath("$.username").value("test-user"));
    }

    @Test
    void shouldAccessProtectedEndpointWhenAuthenticated() throws Exception {
        // Given: Authenticated OAuth2 user
        // When: GET /api/auth/user
        // Then: Returns user info
        mockMvc.perform(get("/api/auth/user")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "test-user")
                                        .claim("email", "test@example.com")
                                        .claim("name", "Test User"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("test-user"))
                .andExpect(jsonPath("$.email").value("test@example.com"));
    }

    @Test
    void shouldDenyAccessToUserEndpointWhenNotAuthenticated() throws Exception {
        // When: GET /api/auth/user without authentication
        // Then: Returns 401 Unauthorized
        mockMvc.perform(get("/api/auth/user"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldIncludeRolesInUserInfo() throws Exception {
        // Given: Authenticated user with roles
        // When: GET /api/auth/user
        // Then: User info includes roles
        mockMvc.perform(get("/api/auth/user")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "admin-user")
                                        .claim("email", "admin@example.com")
                                        .claim("roles", List.of("ADMIN", "USER")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin-user"))
                .andExpect(jsonPath("$.roles").isArray())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(jsonPath("$.roles[1]").value("USER"));
    }

    @Test
    void shouldReturnLoginUrl() throws Exception {
        // When: GET /api/auth/login-url without authentication
        // Then: Returns OAuth2 login URL
        mockMvc.perform(get("/api/auth/login-url"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/oauth2/authorization/frontend-app"));
    }
}
