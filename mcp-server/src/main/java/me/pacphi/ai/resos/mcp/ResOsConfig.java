package me.pacphi.ai.resos.mcp;

import me.pacphi.ai.resos.api.DefaultApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@Configuration
public class ResOsConfig {

    @Value("${default.url}")
    private String apiEndpoint;

    @Bean
    public WebClient resosWebClient() {
        return WebClient.builder()
                .baseUrl(apiEndpoint)
                .build();
    }

    @Bean
    public DefaultApi defaultApi(WebClient resosWebClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(resosWebClient))
                .build();
        return factory.createClient(DefaultApi.class);
    }

}