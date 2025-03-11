package me.pacphi.ai.resos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableConfigurationProperties
public class SpringAiResOsFrontendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringAiResOsFrontendApplication.class, args);
    }

}
