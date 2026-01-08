package me.pacphi.ai.resos.mcp;

import me.pacphi.ai.resos.api.DefaultApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class ResOsConfig {

    private static final Logger log = LoggerFactory.getLogger(ResOsConfig.class);

    @Value("${default.url}")
    private String apiEndpoint;

    @Value("${security.oauth2.enabled:true}")
    private boolean oauth2Enabled;

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(
            ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager(
            ClientRegistrationRepository clientRegistrationRepository,
            OAuth2AuthorizedClientService authorizedClientService) {

        var authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .refreshToken()
            .build();

        // Use AuthorizedClientServiceOAuth2AuthorizedClientManager for service-to-service
        // client_credentials flow (no web request context needed)
        var authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
            clientRegistrationRepository,
            authorizedClientService);
        authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);

        return authorizedClientManager;
    }

    @Bean
    public RestClient resosRestClient(OAuth2AuthorizedClientManager authorizedClientManager) {
        log.info("Creating RestClient with baseUrl: {} (OAuth2 enabled: {})", apiEndpoint, oauth2Enabled);

        // Configure JDK HttpClient with timeout
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(apiEndpoint)
                .requestFactory(requestFactory)
                .requestInterceptor(logRequestInterceptor())
                .requestInterceptor(logResponseInterceptor());

        // Add OAuth2 interceptor if enabled
        if (oauth2Enabled) {
            builder.requestInterceptor(oauth2Interceptor(authorizedClientManager));
            log.info("OAuth2 client credentials interceptor enabled for mcp-server client");
        }

        return builder.build();
    }

    private ClientHttpRequestInterceptor logRequestInterceptor() {
        return (request, body, execution) -> {
            log.debug("Request: {} {}", request.getMethod(), request.getURI());
            request.getHeaders().forEach((name, values) ->
                values.forEach(value -> log.debug("Request Header: {}={}", name,
                    name.equalsIgnoreCase("Authorization") ? "[REDACTED]" : value)));
            return execution.execute(request, body);
        };
    }

    private ClientHttpRequestInterceptor logResponseInterceptor() {
        return (request, body, execution) -> {
            ClientHttpResponse response = execution.execute(request, body);
            log.debug("Response: {} from {}", response.getStatusCode(), request.getURI());
            return response;
        };
    }

    private ClientHttpRequestInterceptor oauth2Interceptor(
            OAuth2AuthorizedClientManager authorizedClientManager) {
        return (request, body, execution) -> {
            try {
                // Build OAuth2AuthorizeRequest for client_credentials flow
                org.springframework.security.oauth2.client.OAuth2AuthorizeRequest authorizeRequest =
                        org.springframework.security.oauth2.client.OAuth2AuthorizeRequest
                                .withClientRegistrationId("mcp-server")
                                .principal("mcp-server")  // Service account principal
                                .build();

                // Get authorized client (with token)
                org.springframework.security.oauth2.client.OAuth2AuthorizedClient authorizedClient =
                        authorizedClientManager.authorize(authorizeRequest);

                if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                    String token = authorizedClient.getAccessToken().getTokenValue();
                    request.getHeaders().setBearerAuth(token);
                    log.debug("Added OAuth2 Bearer token to request");
                } else {
                    log.warn("Failed to obtain OAuth2 access token for mcp-server client");
                }
            } catch (Exception e) {
                log.error("Error obtaining OAuth2 token", e);
            }

            return execution.execute(request, body);
        };
    }

    @Bean
    public DefaultApi defaultApi(RestClient resosRestClient) {
        HttpServiceProxyFactory factory = HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(resosRestClient))
                .build();
        return factory.createClient(DefaultApi.class);
    }

}
