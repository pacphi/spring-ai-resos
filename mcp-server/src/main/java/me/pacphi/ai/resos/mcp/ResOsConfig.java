package me.pacphi.ai.resos.mcp;

import feign.Contract;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResOsConfig {

    @Bean
    public Contract feignContract() {
        return new SpringMvcContract();
    }

}