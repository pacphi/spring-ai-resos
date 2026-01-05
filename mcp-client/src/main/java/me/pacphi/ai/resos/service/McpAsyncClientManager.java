package me.pacphi.ai.resos.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpAsyncClient;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.WebFluxSseClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.json.McpJsonMapper;

import org.springframework.ai.mcp.client.common.autoconfigure.NamedClientMcpTransport;
import org.springframework.ai.mcp.client.common.autoconfigure.configurer.McpAsyncClientConfigurer;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpClientCommonProperties;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpSseClientProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Custom MCP client manager that creates fresh MCP async clients per request.
 * This is a workaround for Spring AI MCP SSE connection issues where connections
 * can drop and don't automatically reconnect (see GitHub issue #2740).
 */
@Component
public class McpAsyncClientManager {

    private final McpAsyncClientConfigurer mcpSyncClientConfigurer;
    private final McpClientCommonProperties mcpClientCommonProperties;
    private final McpSseClientProperties mcpSseClientProperties;
    private final WebClient.Builder webClientBuilderTemplate;
    private final McpJsonMapper jsonMapper;

    public McpAsyncClientManager(McpAsyncClientConfigurer mcpSyncClientConfigurer,
                                 McpClientCommonProperties mcpClientCommonProperties,
                                 McpSseClientProperties mcpSseClientProperties,
                                 WebClient.Builder webClientBuilderTemplate,
                                 ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.mcpSyncClientConfigurer = mcpSyncClientConfigurer;
        this.mcpClientCommonProperties = mcpClientCommonProperties;
        this.mcpSseClientProperties = mcpSseClientProperties;
        this.webClientBuilderTemplate = webClientBuilderTemplate;
        // Create JacksonMcpJsonMapper internally - McpJsonMapper is not exposed as a bean in Spring AI 2.0
        // Use ObjectProvider with fallback to new ObjectMapper, same pattern as Spring AI autoconfiguration
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        this.jsonMapper = new JacksonMcpJsonMapper(objectMapper);
    }

    public List<McpAsyncClient> newMcpAsyncClients() {

        List<NamedClientMcpTransport> namedTransports = new ArrayList<>();

        for (Map.Entry<String, McpSseClientProperties.SseParameters> serverParameters : mcpSseClientProperties.getConnections().entrySet()) {
            var webClientBuilder = webClientBuilderTemplate.clone().baseUrl(serverParameters.getValue().url());
            var transport = new WebFluxSseClientTransport(webClientBuilder, jsonMapper);
            namedTransports.add(new NamedClientMcpTransport(serverParameters.getKey(), transport));
        }

        List<McpAsyncClient> mcpAsyncClients = new ArrayList<>();

        if (!CollectionUtils.isEmpty(namedTransports)) {
            for (NamedClientMcpTransport namedTransport : namedTransports) {

                McpSchema.Implementation clientInfo = new McpSchema.Implementation(mcpClientCommonProperties.getName(),
                        mcpClientCommonProperties.getVersion());

                McpClient.AsyncSpec syncSpec = McpClient.async(namedTransport.transport())
                        .clientInfo(clientInfo)
                        .requestTimeout(mcpClientCommonProperties.getRequestTimeout());

                syncSpec = mcpSyncClientConfigurer.configure(namedTransport.name(), syncSpec);

                var syncClient = syncSpec.build();

                if (mcpClientCommonProperties.isInitialized()) {
                    syncClient.initialize().block();
                }

                mcpAsyncClients.add(syncClient);
            }
        }

        return mcpAsyncClients;
    }

}
