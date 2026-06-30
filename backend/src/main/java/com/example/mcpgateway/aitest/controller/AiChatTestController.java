package com.example.mcpgateway.aitest.controller;

import com.example.mcpgateway.aitest.application.service.AiChatSessionManager;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-chat")
public class AiChatTestController {
    private final AiChatSessionManager sessionManager;
    public AiChatTestController(AiChatSessionManager sessionManager) { this.sessionManager = sessionManager; }

    @PostMapping("/sessions")
    AiChatSessionManager.SessionInfo createSession(@Valid @RequestBody CreateSessionRequest req) {
        if (req.serverIds() != null && !req.serverIds().isEmpty()) {
            return sessionManager.startSession(req.serverIds(), req.modelConfigId(), req.mcpKeys());
        }
        return sessionManager.startSession(req.serverId(), req.modelConfigId(), req.mcpKey());
    }

    @PostMapping("/sessions/{sessionId}/chat")
    AiChatSessionManager.ChatReply chat(@PathVariable String sessionId, @Valid @RequestBody ChatRequest req) {
        return sessionManager.sendMessage(sessionId, req.message());
    }

    @DeleteMapping("/sessions/{sessionId}")
    void closeSession(@PathVariable String sessionId) {
        sessionManager.closeSession(sessionId);
    }

    public record CreateSessionRequest(Long serverId, List<Long> serverIds, @NotNull Long modelConfigId,
                                       String mcpKey, Map<Long, String> mcpKeys) {}
    public record ChatRequest(@NotBlank String message) {}
}
