package com.ohgiraffers.handlermethod.api;

import com.ohgiraffers.handlermethod.dto.AiChatRequest;
import com.ohgiraffers.handlermethod.dto.AiChatResponse;
import com.ohgiraffers.handlermethod.service.AiService;
import com.ohgiraffers.handlermethod.service.AiStreamingService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final AiStreamingService aiStreamingService;

    public AiController(AiService aiService, AiStreamingService aiStreamingService) {
        this.aiService = aiService;
        this.aiStreamingService = aiStreamingService;
    }

    @PostMapping("/chat")
    public AiChatResponse chat(@Valid @RequestBody AiChatRequest request) {
        return aiService.chat(request);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody AiChatRequest request) {
        SseEmitter emitter = new SseEmitter(60_000L);
        aiStreamingService.stream(request, emitter);
        return emitter;
    }
}
