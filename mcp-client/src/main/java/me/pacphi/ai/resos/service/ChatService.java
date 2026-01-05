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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


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
                AsyncMcpToolCallbackProvider.builder().mcpClients(asyncClientManager.newMcpAsyncClients()).build();
        return constructRequest(question)
                .toolCallbacks(provider.getToolCallbacks())
                .stream()
                .content();
    }

    private ChatClient.ChatClientRequestSpec constructRequest(String question) {
        return chatClient
                .prompt()
                .system(buildSystemPrompt())
                .user(question);
    }

    private String buildSystemPrompt() {
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        String twoYearsAgo = LocalDate.now().minusYears(2).format(DateTimeFormatter.ISO_LOCAL_DATE);

        return """
            You are a helpful assistant for a restaurant reservation system.

            Today's date is %s.

            IMPORTANT GUIDELINES FOR USING TOOLS:

            1. START SIMPLE: For questions like "how many" or "list all", call tools WITHOUT filters first.
               - "How many customers gave feedback?" → call getFeedback() with NO customQuery, then count results
               - "Show me all feedback" → call getFeedback() with NO customQuery

            2. ONLY FILTER WHEN EXPLICITLY NEEDED: Use customQuery only when the user specifies criteria.
               - "Show feedback with rating >= 4" → use customQuery: rating >= 4
               - "Show feedback from last 2 years" → use customQuery: created_at >= '%s'

            3. DATE FORMATTING: Use literal ISO dates, NOT SQL functions.
               - Correct: created_at >= '%s'
               - Wrong: created_at >= NOW() - INTERVAL '2 years'

            4. COUNTING: To count records, fetch them and count the list length. Don't add unnecessary filters.

            5. FEEDBACK DATA: Each feedback record has a customer reference. All feedback is from customers.
               Do NOT filter by "customer_id IS NOT NULL" - all feedback has customers.
            """.formatted(today, twoYearsAgo, twoYearsAgo);
    }
}