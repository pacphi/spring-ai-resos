package me.pacphi.ai.resos.csv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile(value = { "dev", "seed" })
public class CsvResourceLoader {

    private static final Logger log = LoggerFactory.getLogger(CsvResourceLoader.class);

    private final List<String> configuredPaths;

    public CsvResourceLoader(CsvProperties csvProperties) {
        this.configuredPaths =
                csvProperties
                    .files()
                        .stream()
                        .map(f -> csvProperties.basePath() + File.separator + f).toList();
    }

    public List<Path> resolveDataFiles() {
        List<Path> resolvedPaths = new ArrayList<>();

        // Try different base paths
        List<Path> basePaths = getBasePaths();

        for (String configuredPath : configuredPaths) {
            Path resolved = resolveDataFile(configuredPath, basePaths);
            if (resolved != null) {
                resolvedPaths.add(resolved);
            }
        }

        if (resolvedPaths.isEmpty()) {
            log.warn("No CSV files could be resolved from configured paths: {}", configuredPaths);
        }

        return resolvedPaths;
    }

    private List<Path> getBasePaths() {
        List<Path> paths = new ArrayList<>();

        // Current working directory (for mvn spring-boot:run)
        paths.add(Paths.get(".").toAbsolutePath().normalize());

        // Project root directory (for tests)
        String userDir = System.getProperty("user.dir");
        if (userDir != null) {
            paths.add(Paths.get(userDir));
        }

        // Add maven target/test-classes for test context
        if (userDir != null) {
            paths.add(Paths.get(userDir, "target", "test-classes"));
        }

        return paths;
    }

    private Path resolveDataFile(String configuredPath, List<Path> basePaths) {
        Path path = Paths.get(configuredPath);

        // If absolute path and exists, use it
        if (path.isAbsolute() && Files.exists(path)) {
            return path;
        }

        // Try each base path
        for (Path basePath : basePaths) {
            Path resolved = basePath.resolve(path).normalize();
            if (Files.exists(resolved)) {
                log.debug("Resolved {} to {}", configuredPath, resolved);
                return resolved;
            }
        }

        log.warn("Could not resolve CSV file: {}", configuredPath);
        return null;
    }
}
