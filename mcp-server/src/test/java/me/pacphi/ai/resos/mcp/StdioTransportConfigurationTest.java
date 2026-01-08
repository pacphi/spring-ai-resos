package me.pacphi.ai.resos.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify STDIO profile configuration.
 * When stdio profile is active, OAuth2 beans should NOT be loaded and
 * the security configuration should be disabled.
 *
 * This test verifies that the conditional bean logic works correctly
 * for Claude Desktop STDIO transport integration.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.main.web-application-type=none",
                "spring.ai.mcp.server.stdio=true"
        }
)
@ActiveProfiles("stdio")
class StdioTransportConfigurationTest {

    @Autowired
    private ApplicationContext context;

    @Value("${security.oauth2.enabled}")
    private boolean oauth2Enabled;

    @Test
    void shouldDisableOAuth2InStdioMode() {
        assertThat(oauth2Enabled)
                .as("OAuth2 should be disabled in STDIO mode")
                .isFalse();
    }

    @Test
    void shouldNotLoadOAuth2AuthorizedClientManager() {
        // OAuth2AuthorizedClientManager should not be in context when OAuth2 is disabled
        String[] beanNames = context.getBeanNamesForType(OAuth2AuthorizedClientManager.class);
        assertThat(beanNames)
                .as("OAuth2AuthorizedClientManager should not be loaded in STDIO mode")
                .isEmpty();
    }

    @Test
    void shouldNotLoadSecurityConfig() {
        // SecurityConfig should not be loaded when security.oauth2.enabled=false
        String[] beanNames = context.getBeanNamesForType(SecurityConfig.class);
        assertThat(beanNames)
                .as("SecurityConfig should not be loaded in STDIO mode")
                .isEmpty();
    }

    @Test
    void shouldLoadResOsConfig() {
        // ResOsConfig should still be present (it's not conditional)
        assertThat(context.getBean(ResOsConfig.class))
                .as("ResOsConfig should be loaded in STDIO mode")
                .isNotNull();
    }

    @Test
    void shouldLoadResOsService() {
        // ResOsService (MCP tools) should still be present
        assertThat(context.getBean(ResOsService.class))
                .as("ResOsService (MCP tools) should be loaded in STDIO mode")
                .isNotNull();
    }
}
