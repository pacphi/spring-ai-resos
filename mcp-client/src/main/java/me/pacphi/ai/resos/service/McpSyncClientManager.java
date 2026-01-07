package me.pacphi.ai.resos.service;

import io.modelcontextprotocol.client.McpSyncClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Simple wrapper for MCP sync clients.
 * Uses autoconfigured clients from Spring AI MCP starter.
 */
@Component
public class McpSyncClientManager {

    private final ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider;

    public McpSyncClientManager(ObjectProvider<List<McpSyncClient>> mcpSyncClientsProvider) {
        this.mcpSyncClientsProvider = mcpSyncClientsProvider;
    }

    public List<McpSyncClient> newMcpSyncClients() {
        return mcpSyncClientsProvider.getIfAvailable(List::of);
    }

}
