package me.pacphi.ai.resos.csv;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

import java.util.List;

@Profile(value = { "dev", "seed", "test" })
@ConfigurationProperties(prefix = "app.seed.csv")
public record CsvProperties(String basePath, List<String> files) {
}