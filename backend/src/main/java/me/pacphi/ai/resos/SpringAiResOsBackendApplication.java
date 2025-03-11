package me.pacphi.ai.resos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringAiResOsBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiResOsBackendApplication.class, args);
    }
}
