package me.pacphi.ai.resos.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;

/**
 * Debug filter to log incoming request cookies and session info.
 * This helps diagnose session/cookie issues.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Only log API requests to reduce noise
        if (uri.startsWith("/api/")) {
            log.info("=== REQUEST DEBUG: {} {} ===", method, uri);

            // Log cookies with partial session ID for debugging
            Cookie[] cookies = request.getCookies();
            if (cookies != null && cookies.length > 0) {
                log.info("Cookies present: {}",
                    Arrays.stream(cookies)
                        .map(c -> {
                            String value = c.getValue();
                            if (c.getName().contains("SESSION") && value.length() > 8) {
                                // Show first 4 and last 4 chars for session ID comparison
                                return c.getName() + "=" + value.substring(0, 4) + "..." + value.substring(value.length() - 4);
                            }
                            return c.getName() + "=" + value.substring(0, Math.min(10, value.length())) + "...";
                        })
                        .toList());
            } else {
                log.warn("NO COOKIES in request!");
            }

            // Log session ID from request (partial for comparison)
            String sessionId = request.getRequestedSessionId();
            String sessionIdPartial = sessionId != null && sessionId.length() > 8
                ? sessionId.substring(0, 4) + "..." + sessionId.substring(sessionId.length() - 4)
                : sessionId;
            log.info("Requested Session ID: {}, valid: {}, from cookie: {}",
                sessionIdPartial != null ? sessionIdPartial : "[null]",
                request.isRequestedSessionIdValid(),
                request.isRequestedSessionIdFromCookie());

            // Check if there's an actual session on the server
            var existingSession = request.getSession(false);
            if (existingSession != null) {
                String existingId = existingSession.getId();
                String existingIdPartial = existingId.length() > 8
                    ? existingId.substring(0, 4) + "..." + existingId.substring(existingId.length() - 4)
                    : existingId;
                log.info("Server session exists: {}, matches request: {}",
                    existingIdPartial,
                    existingId.equals(sessionId));
            } else {
                log.warn("NO server session exists for this request!");
            }

            // Log relevant headers
            String cookieHeader = request.getHeader("Cookie");
            log.info("Cookie header: {}", cookieHeader != null ? "[present, length=" + cookieHeader.length() + "]" : "[null]");
        }

        filterChain.doFilter(request, response);
    }
}
