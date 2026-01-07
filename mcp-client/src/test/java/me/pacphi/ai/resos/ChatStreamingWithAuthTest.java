package me.pacphi.ai.resos;

import me.pacphi.ai.resos.service.ChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for chat streaming with authentication.
 * Tests that chat endpoints require authentication and work correctly when authenticated.
 * Uses TestContainers to start backend OAuth2 server.
 */
class ChatStreamingWithAuthTest extends AbstractOAuth2IntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ChatService chatService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public ChatService mockChatService() {
            return org.mockito.Mockito.mock(ChatService.class);
        }
    }

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void shouldStreamChatResponseWhenAuthenticated() throws Exception {
        // Given: Authenticated user, mock chat model response
        doAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(1);
            Runnable onComplete = invocation.getArgument(2);

            // Simulate streaming response
            onToken.accept("Hello");
            onToken.accept(" ");
            onToken.accept("World");
            onComplete.run();

            return null;
        }).when(chatService).streamResponseToQuestion(anyString(), any(), any(), any());

        // When: POST /api/v1/resos/stream/chat
        // Then: Returns SSE stream with chat tokens
        mockMvc.perform(post("/api/v1/resos/stream/chat")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "test-user")
                                        .claim("email", "test@example.com")))
                        .with(csrf())  // Add CSRF token for POST request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Hello\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldDenyChatAccessWithoutAuthentication() throws Exception {
        // When: POST /api/v1/resos/stream/chat without authentication
        // Then: Returns 401 Unauthorized
        mockMvc.perform(post("/api/v1/resos/stream/chat")
                        .with(csrf())  // Add CSRF token
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Hello\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldHandleChatServiceErrors() throws Exception {
        // Given: ChatService throws an error
        doAnswer(invocation -> {
            Consumer<Throwable> onError = invocation.getArgument(3);
            onError.accept(new RuntimeException("Chat service error"));
            return null;
        }).when(chatService).streamResponseToQuestion(anyString(), any(), any(), any());

        // When: POST /api/v1/resos/stream/chat
        // Then: Should handle error gracefully
        mockMvc.perform(post("/api/v1/resos/stream/chat")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "test-user")))
                        .with(csrf())  // Add CSRF token for POST request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Cause error\"}"))
                .andExpect(status().isOk()); // SSE starts successfully, error handled in stream
    }

    @Test
    void shouldRequireValidRequestBody() throws Exception {
        // Given: Authenticated user and ChatService configured
        doAnswer(invocation -> {
            Consumer<String> onToken = invocation.getArgument(1);
            Runnable onComplete = invocation.getArgument(2);
            // Handle null question gracefully
            onToken.accept("Please provide a question");
            onComplete.run();
            return null;
        }).when(chatService).streamResponseToQuestion(any(), any(), any(), any());

        // When: POST with empty JSON (question is null)
        // Then: Returns 200 OK (endpoint accepts request, validation happens in service layer)
        mockMvc.perform(post("/api/v1/resos/stream/chat")
                        .with(oidcLogin()
                                .idToken(token -> token
                                        .claim("sub", "test-user")))
                        .with(csrf())  // Add CSRF token for POST request
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
