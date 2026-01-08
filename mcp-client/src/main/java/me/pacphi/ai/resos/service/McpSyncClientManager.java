package me.pacphi.ai.resos.service;

import io.modelcontextprotocol.client.McpSyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Manager for MCP sync clients with lazy initialization.
 * <p>
 * Because OAuth2 tokens aren't ready at startup, we set
 * spring.ai.mcp.client.initialized=false and manually initialize
 * clients on first use when OAuth2 context is available.
 * </p>
 */
@Component
public class McpSyncClientManager {

    private static final Logger log = LoggerFactory.getLogger(McpSyncClientManager.class);

    private final ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider;
    private final ConcurrentMap<McpSyncClient, Boolean> initializedClients = new ConcurrentHashMap<>();

    public McpSyncClientManager(ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider) {
        this.mcpSyncClientsProvider = mcpSyncClientsProvider;
    }

    /**
     * Get MCP sync clients, initializing them on first access.
     * This ensures OAuth2 tokens are available when initialization occurs.
     */
    public List<McpSyncClient> newMcpSyncClients() {
        List<McpSyncClient> clients = mcpSyncClientsProvider.getIfAvailable(List::of);

        for (McpSyncClient client : clients) {
            initializeIfNeeded(client);
        }

        return clients;
    }

    private void initializeIfNeeded(McpSyncClient client) {
        initializedClients.computeIfAbsent(client, c -> {
            try {
                log.info("Initializing MCP client: {}", c);
                c.initialize();
                log.info("MCP client initialized successfully: {}", c);
                return true;
            } catch (Exception e) {
                log.error("Failed to initialize MCP client: {}", c, e);
                throw new RuntimeException("MCP client initialization failed", e);
            }
        });
    }

}
