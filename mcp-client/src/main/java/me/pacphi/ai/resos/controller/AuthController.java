package me.pacphi.ai.resos.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Authentication controller for the React SPA.
 * Provides endpoints to check auth status and get current user info.
 * WebMVC-based (servlet stack) implementation.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Get current user information if authenticated.
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(@AuthenticationPrincipal OidcUser user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("username", user.getName());
        userInfo.put("email", user.getEmail());
        userInfo.put("name", user.getFullName() != null ? user.getFullName() : user.getName());

        // Extract roles from token claims
        Object rolesClaim = user.getClaim("roles");
        if (rolesClaim instanceof List<?> roles) {
            userInfo.put("roles", roles);
        } else {
            userInfo.put("roles", List.of());
        }

        // Extract additional claims if present
        if (user.getClaim("authorities") != null) {
            userInfo.put("authorities", user.getClaim("authorities"));
        }

        return ResponseEntity.ok(userInfo);
    }

    /**
     * Check authentication status.
     * Returns whether the user is authenticated without requiring authentication.
     */
    @GetMapping("/status")
    public Map<String, Object> getAuthStatus() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !authentication.getPrincipal().equals("anonymousUser")) {
            Map<String, Object> status = new HashMap<>();
            status.put("authenticated", true);
            status.put("username", authentication.getName());
            return status;
        }

        return Map.of(
            "authenticated", false,
            "loginUrl", "/oauth2/authorization/frontend-app"
        );
    }

    /**
     * Get the login URL for OAuth2.
     */
    @GetMapping("/login-url")
    public Map<String, String> getLoginUrl() {
        return Map.of("url", "/oauth2/authorization/frontend-app");
    }
}
