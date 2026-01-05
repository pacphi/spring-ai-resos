package me.pacphi.ai.resos.mcp;

import me.pacphi.ai.resos.api.DefaultApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

@Configuration
public class ResOsConfig {

    private static final Logger log = LoggerFactory.getLogger(ResOsConfig.class);

    @Value("${default.url}")
    private String apiEndpoint;

    @Bean
    public WebClient resosWebClient() {
        log.info("Creating WebClient with baseUrl: {}", apiEndpoint);

        HttpClient httpClient = HttpClient.create()
                .protocol(reactor.netty.http.HttpProtocol.HTTP11)
                .responseTimeout(Duration.ofSeconds(30))
                .wiretap("reactor.netty.http.client.HttpClient",
                         io.netty.handler.logging.LogLevel.DEBUG,
                         reactor.netty.transport.logging.AdvancedByteBufFormat.TEXTUAL);

        return WebClient.builder()
                .baseUrl(apiEndpoint)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(logRequest())
                .filter(logResponse())
                .build();
    }

    private ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(request -> {
            log.debug("Request: {} {}", request.method(), request.url());
            return Mono.just(request);
        });
    }

    private ExchangeFilterFunction logResponse() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            log.debug("Response: {} from {}", response.statusCode(), response.request().getURI());
            return Mono.just(response);
        });
    }

    @Bean
    public DefaultApi defaultApi(WebClient resosWebClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(WebClientAdapter.create(resosWebClient))
                .build();
        return factory.createClient(DefaultApi.class);
    }

}