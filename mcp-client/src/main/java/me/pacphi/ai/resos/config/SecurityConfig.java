package me.pacphi.ai.resos.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Security configuration for the MCP Client (frontend) application.
 * Uses OAuth2 login with Authorization Code + PKCE flow.
 * WebMVC-based (servlet stack) configuration.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(authorize -> authorize
                // Static assets
                .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico", "/vite.svg").permitAll()

                // Actuator endpoints
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                // Auth endpoints (need to work both authenticated and unauthenticated)
                .requestMatchers("/api/auth/status", "/api/auth/login-url").permitAll()

                // Login error page and error endpoint (prevent session creation before login)
                .requestMatchers("/login", "/error").permitAll()

                // All API endpoints require authentication
                .requestMatchers("/api/**").authenticated()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository())
                )
                .successHandler(authenticationSuccessHandler())
                .failureHandler(authenticationFailureHandler())
            )
            .logout(logout -> logout
                .logoutUrl("/logout")
                // Allow GET requests for logout (browser navigation)
                .logoutRequestMatcher(request ->
                    request.getServletPath().equals("/logout"))
                .logoutSuccessHandler(logoutSuccessHandler())
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID")
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) -> {
                    // For API requests, return 401 instead of redirecting
                    if (request.getRequestURI().startsWith("/api/")) {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Unauthorized");
                    } else {
                        // For other requests, redirect to OAuth2 login
                        response.sendRedirect("/oauth2/authorization/frontend-app");
                    }
                })
            )
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf
                // Disable CSRF for API endpoints - they use JSON content type which
                // cannot be submitted by HTML forms, and SameSite cookies provide protection
                .ignoringRequestMatchers("/api/**")
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .build();
    }

    @Bean
    public HttpCookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
        return new HttpCookieOAuth2AuthorizationRequestRepository();
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        SimpleUrlAuthenticationSuccessHandler handler = new SimpleUrlAuthenticationSuccessHandler();
        handler.setDefaultTargetUrl("/");
        handler.setAlwaysUseDefaultTargetUrl(false);
        return handler;
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            logger.error("OAuth2 authentication failed: {}", exception.getMessage(), exception);
            // Log the full exception for debugging
            if (exception.getCause() != null) {
                logger.error("Root cause: {}", exception.getCause().getMessage(), exception.getCause());
            }
            // Return a simple error response instead of redirecting (breaks the loop)
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(
                "<html><body><h1>OAuth2 Authentication Failed</h1>" +
                "<p>Error: " + exception.getMessage() + "</p>" +
                "<p><a href='/oauth2/authorization/frontend-app'>Try again</a></p>" +
                "</body></html>"
            );
        };
    }

    @Bean
    public LogoutSuccessHandler logoutSuccessHandler() {
        SimpleUrlLogoutSuccessHandler handler = new SimpleUrlLogoutSuccessHandler();
        handler.setDefaultTargetUrl("/");
        return handler;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:8081",
            "http://localhost:3000",
            "http://localhost:5173"  // Vite dev server
        ));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
