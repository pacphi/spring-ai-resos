package me.pacphi.ai.resos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Detects the Docker socket location for different Docker implementations.
 * Supports: Docker Desktop, OrbStack, Rancher Desktop, Colima, and Podman.
 */
public class DockerEnvironmentDetector {

    private static final Logger log = LoggerFactory.getLogger(DockerEnvironmentDetector.class);

    /**
     * Detect the Docker socket location.
     * Checks common locations in order of preference.
     *
     * @return Docker socket URI, or null if not found
     */
    public static String detectDockerSocket() {
        // Check environment variable first
        String dockerHost = System.getenv("DOCKER_HOST");
        if (dockerHost != null && !dockerHost.isEmpty()) {
            log.info("Using DOCKER_HOST from environment: {}", dockerHost);
            return dockerHost;
        }

        // Check TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE
        String override = System.getenv("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE");
        if (override != null && !override.isEmpty()) {
            log.info("Using TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE: {}", override);
            return "unix://" + override;
        }

        // Common Docker socket locations (in order of preference)
        List<String> socketPaths = List.of(
                // OrbStack (macOS)
                System.getProperty("user.home") + "/.orbstack/run/docker.sock",
                // Docker Desktop (macOS/Linux)
                System.getProperty("user.home") + "/.docker/run/docker.sock",
                // Rancher Desktop
                System.getProperty("user.home") + "/.rd/docker.sock",
                // Colima
                System.getProperty("user.home") + "/.colima/docker.sock",
                // Standard Linux location
                "/var/run/docker.sock",
                // Podman (macOS)
                System.getProperty("user.home") + "/.local/share/containers/podman/machine/podman.sock"
        );

        for (String socketPath : socketPaths) {
            Path path = Paths.get(socketPath);
            if (Files.exists(path)) {
                String uri = "unix://" + socketPath;
                log.info("Detected Docker socket at: {}", uri);
                return uri;
            }
        }

        log.warn("Could not detect Docker socket. Checked locations: {}", socketPaths);
        log.warn("Set DOCKER_HOST environment variable or TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE to specify location.");
        return null;
    }
}
