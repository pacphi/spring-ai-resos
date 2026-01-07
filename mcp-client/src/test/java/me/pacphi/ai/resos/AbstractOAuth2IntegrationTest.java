package me.pacphi.ai.resos;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;

/**
 * Base class for mcp-client integration tests that require the backend OAuth2 server.
 *
 * Starts the backend application in a Docker container using TestContainers and configures
 * the mcp-client to use the containerized backend for OAuth2 authentication.
 *
 * Since the main application.yml now uses explicit URIs instead of issuer-uri,
 * we can use standard @DynamicPropertySource pattern to override with container URLs.
 *
 * Build backend image: cd backend && docker build -f Dockerfile.test -t spring-ai-resos-backend:test .
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractOAuth2IntegrationTest {

    /**
     * Backend container running the OAuth2 Authorization Server.
     */
    @Container
    static GenericContainer<?> backendContainer = new GenericContainer<>(
            DockerImageName.parse("spring-ai-resos-backend:test"))
            .withExposedPorts(8080)
            .withLogConsumer(outputFrame -> System.out.print(outputFrame.getUtf8String()))
            .waitingFor(Wait.forHttp("/actuator/health")
                    .forPort(8080)
                    .forStatusCode(200)
                    .withStartupTimeout(Duration.ofMinutes(2)))
            .withReuse(false);  // Don't reuse - ensure clean state per test

    /**
     * Configure mcp-client to use the containerized backend.
     *
     * Overrides AUTH_SERVER_URL environment variable with the actual TestContainers URL.
     * Since we use explicit URIs in application.yml, this cleanly replaces all endpoints.
     */
    @DynamicPropertySource
    static void configureBackendProperties(DynamicPropertyRegistry registry) {
        String backendUrl = "http://" + backendContainer.getHost() + ":" + backendContainer.getMappedPort(8080);

        // Override AUTH_SERVER_URL which is used in all OAuth2 endpoint URIs
        registry.add("AUTH_SERVER_URL", () -> backendUrl);
    }

    /**
     * Get the backend container's base URL.
     */
    protected static String getBackendUrl() {
        return "http://" + backendContainer.getHost() + ":" + backendContainer.getMappedPort(8080);
    }
}
