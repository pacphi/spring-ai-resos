package me.pacphi.ai.resos.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Customizes JWT tokens to include user roles and other claims.
 */
@Configuration
public class JwtTokenCustomizer {

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer() {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                Authentication principal = context.getPrincipal();

                // Add roles to the token
                Set<String> roles = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .collect(Collectors.toSet());

                if (!roles.isEmpty()) {
                    context.getClaims().claim("roles", roles);
                }

                // Add all authorities (including scopes)
                Set<String> authorities = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());

                context.getClaims().claim("authorities", authorities);
            }

            // For ID tokens, add additional user info
            if (context.getTokenType().getValue().equals("id_token")) {
                Authentication principal = context.getPrincipal();

                // Add preferred_username claim (standard OIDC claim)
                context.getClaims().claim("preferred_username", principal.getName());

                // Add roles to ID token as well
                Set<String> roles = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(auth -> auth.startsWith("ROLE_"))
                    .collect(Collectors.toSet());

                if (!roles.isEmpty()) {
                    context.getClaims().claim("roles", roles);
                }
            }
        };
    }
}
