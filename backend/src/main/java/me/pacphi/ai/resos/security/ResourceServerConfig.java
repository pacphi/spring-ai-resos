package me.pacphi.ai.resos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource Server configuration for protecting API endpoints.
 * Validates JWT tokens and enforces authorization rules.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    @Order(2)
    public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**", "/customers/**", "/bookings/**", "/feedback/**",
                           "/tables/**", "/opening-hours/**", "/orders/**", "/healthcheck/**")
            .authorizeHttpRequests(authorize -> authorize
                // Public endpoints
                .requestMatchers("/healthcheck/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()

                // Read operations - require backend.read scope or USER role
                .requestMatchers(HttpMethod.GET, "/customers/**").hasAnyAuthority("SCOPE_backend.read", "ROLE_USER", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/bookings/**").hasAnyAuthority("SCOPE_backend.read", "ROLE_USER", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/feedback/**").hasAnyAuthority("SCOPE_backend.read", "ROLE_USER", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/tables/**").hasAnyAuthority("SCOPE_backend.read", "ROLE_USER", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/opening-hours/**").hasAnyAuthority("SCOPE_backend.read", "ROLE_USER", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.GET, "/orders/**").hasAnyAuthority("SCOPE_backend.read", "ROLE_USER", "ROLE_OPERATOR", "ROLE_ADMIN")

                // Write operations - require backend.write scope or OPERATOR role
                .requestMatchers(HttpMethod.POST, "/bookings/**").hasAnyAuthority("SCOPE_backend.write", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/bookings/**").hasAnyAuthority("SCOPE_backend.write", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/bookings/**").hasAnyAuthority("SCOPE_backend.write", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/orders/**").hasAnyAuthority("SCOPE_backend.write", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.PUT, "/orders/**").hasAnyAuthority("SCOPE_backend.write", "ROLE_OPERATOR", "ROLE_ADMIN")
                .requestMatchers(HttpMethod.POST, "/feedback/**").hasAnyAuthority("SCOPE_backend.write", "ROLE_OPERATOR", "ROLE_ADMIN")

                // Admin operations
                .requestMatchers("/customers/**").hasAuthority("ROLE_ADMIN")

                // Default: require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Don't add a prefix - our scopes already have the format we want
        grantedAuthoritiesConverter.setAuthorityPrefix("");
        // Look for authorities in the 'scope' claim (default) and 'roles' claim
        grantedAuthoritiesConverter.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);

        return jwtAuthenticationConverter;
    }
}
