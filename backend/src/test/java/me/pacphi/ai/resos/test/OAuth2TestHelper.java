package me.pacphi.ai.resos.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Helper utility for OAuth2-related test operations.
 * Provides methods to obtain tokens, decode JWTs, and build authenticated requests.
 */
public class OAuth2TestHelper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Obtain an OAuth2 access token using client_credentials grant type.
     *
     * @param tokenEndpoint Full token endpoint URL (e.g., http://localhost:8080/oauth2/token)
     * @param clientId OAuth2 client ID
     * @param clientSecret OAuth2 client secret
     * @param scopes Optional scopes to request
     * @return Access token string
     */
    public static String obtainClientCredentialsToken(
            String tokenEndpoint,
            String clientId,
            String clientSecret,
            String... scopes) {

        // Build request body
        StringBuilder body = new StringBuilder("grant_type=client_credentials");
        if (scopes != null && scopes.length > 0) {
            body.append("&scope=").append(String.join(" ", scopes));
        }

        // Create RestClient for the request
        RestClient restClient = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .build();

        try {
            String response = restClient.post()
                    .uri(tokenEndpoint)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " +
                            Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8)))
                    .body(body.toString())
                    .retrieve()
                    .body(String.class);

            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode.get("access_token").asText();
        } catch (Exception e) {
            throw new RuntimeException("Failed to obtain token from " + tokenEndpoint, e);
        }
    }

    /**
     * Decode JWT and extract claims (header + payload).
     * Note: This does NOT validate the signature, it's for inspection only.
     *
     * @param token JWT token string
     * @return Decoded JWT payload as JSON string
     */
    public static String decodeJwtPayload(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            // Decode the payload (second part)
            String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return payload;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode JWT", e);
        }
    }

    /**
     * Decode JWT header.
     *
     * @param token JWT token string
     * @return Decoded JWT header as JSON string
     */
    public static String decodeJwtHeader(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }

            // Decode the header (first part)
            String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            return header;
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode JWT header", e);
        }
    }

    /**
     * Create a MockMvc request builder with Bearer token authentication.
     *
     * @param requestBuilder Base request builder
     * @param token Bearer token
     * @return Request builder with Authorization header
     */
    public static MockHttpServletRequestBuilder withBearerToken(
            MockHttpServletRequestBuilder requestBuilder,
            String token) {
        return requestBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    /**
     * Extract a specific claim from a decoded JWT payload.
     *
     * @param jwtPayload Decoded JWT payload (JSON string)
     * @param claimName Name of the claim to extract
     * @return Claim value as string, or null if not found
     */
    public static String extractClaim(String jwtPayload, String claimName) {
        try {
            JsonNode jsonNode = objectMapper.readTree(jwtPayload);
            JsonNode claim = jsonNode.get(claimName);
            return claim != null ? claim.asText() : null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract claim: " + claimName, e);
        }
    }

    /**
     * Create an expired token for testing (helper to understand token structure).
     * Note: This is a placeholder - actual implementation would require JWT library.
     *
     * @return A sample expired token string
     */
    public static String createExpiredToken() {
        // This would require a JWT library like nimbus-jose-jwt to create a properly signed token
        // For now, returning a placeholder that tests can use to understand the concept
        return "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0LXVzZXIiLCJleHAiOjAsImlhdCI6MTY0MDAwMDAwMH0.invalid";
    }
}
