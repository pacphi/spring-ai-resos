package me.pacphi.ai.resos.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the MCP Server.
 * Protects MCP endpoints with OAuth2 JWT validation.
 * Based on Baeldung pattern: https://www.baeldung.com/spring-ai-mcp-servers-oauth2
 *
 * Only active when security.oauth2.enabled=true (default).
 * Disabled for STDIO transport (Claude Desktop integration).
 */
@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "security.oauth2.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                // MCP endpoints require authentication (JWT Bearer tokens)
                .requestMatchers("/mcp/**").authenticated()

                // Actuator endpoints are public (for health checks)
                .requestMatchers("/actuator/**").permitAll()

                // All other requests are permitted (for MCP protocol negotiation)
                .anyRequest().permitAll()
            )
            // Configure as OAuth2 Resource Server with JWT validation
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            )
            // Disable CSRF for stateless API
            .csrf(CsrfConfigurer::disable)
            // Enable CORS
            .cors(Customizer.withDefaults())
            .build();
    }
}
