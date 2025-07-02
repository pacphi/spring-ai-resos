package me.pacphi.ai.resos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.mcp.AsyncMcpToolCallbackProvider;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;


@Service
public class ChatService {
    private final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final ChatClient chatClient;
    private final McpAsyncClientManager asyncClientManager;

    public ChatService(
            ChatModel chatModel,
            ChatMemory chatMemory,
            McpAsyncClientManager asyncClientManager
            ) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build(), new SimpleLoggerAdvisor())
                .build();
        this.asyncClientManager = asyncClientManager;
    }

    public Flux<String> streamResponseToQuestion(String question) {
        AsyncMcpToolCallbackProvider provider =
                new AsyncMcpToolCallbackProvider(
                        asyncClientManager.newMcpAsyncClients()
                );
        return constructRequest(question)
                .tools(provider.getToolCallbacks())
                .stream()
                .content();
    }

    private ChatClient.ChatClientRequestSpec constructRequest(String question) {
        return chatClient
                .prompt()
                .user(question);
    }
}