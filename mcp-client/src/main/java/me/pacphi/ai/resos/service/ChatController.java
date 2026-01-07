package me.pacphi.ai.resos.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;


@RestController
@RequestMapping("/api/v1/resos")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;

    private static record Inquiry(String question) {}

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping(value = "/stream/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody Inquiry inquiry) {
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout

        // Stream response using callbacks
        chatService.streamResponseToQuestion(
            inquiry.question(),
            // onToken callback
            token -> {
                try {
                    emitter.send(SseEmitter.event().data(token));
                } catch (IOException e) {
                    log.error("Error sending SSE token", e);
                    emitter.completeWithError(e);
                }
            },
            // onComplete callback
            () -> {
                log.debug("Chat stream completed");
                emitter.complete();
            },
            // onError callback
            error -> {
                log.error("Error in chat stream", error);
                emitter.completeWithError(error);
            }
        );

        return emitter;
    }

}
