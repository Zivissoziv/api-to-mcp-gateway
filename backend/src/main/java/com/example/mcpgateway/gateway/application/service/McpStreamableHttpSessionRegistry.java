package com.example.mcpgateway.gateway.application.service;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class McpStreamableHttpSessionRegistry {

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final long SSE_TIMEOUT_MS = Duration.ofMinutes(30).toMillis();

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState create(String serverCode, String protocolVersion) {
        String sessionId = UUID.randomUUID().toString();
        SessionState state = new SessionState(sessionId, serverCode, protocolVersion,
                Instant.now().plus(SESSION_TTL));
        sessions.put(sessionId, state);
        return state;
    }

    public Optional<SessionState> find(String sessionId, String serverCode) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        SessionState state = sessions.get(sessionId);
        if (state == null || state.isExpired() || !state.serverCode().equals(serverCode)) {
            if (state != null && state.isExpired()) {
                close(sessionId);
            }
            return Optional.empty();
        }
        state.touch();
        return Optional.of(state);
    }

    public boolean close(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }
        SessionState state = sessions.remove(sessionId);
        if (state == null) {
            return false;
        }
        state.completeEmitters();
        return true;
    }

    public SseEmitter subscribe(String sessionId, String serverCode) {
        SessionState state = find(sessionId, serverCode)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired MCP session"));
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        state.addEmitter(emitter);
        emitter.onCompletion(() -> state.removeEmitter(emitter));
        emitter.onTimeout(() -> state.removeEmitter(emitter));
        emitter.onError(error -> state.removeEmitter(emitter));
        try {
            emitter.send(SseEmitter.event().name("endpoint").data("/mcp/" + serverCode));
        } catch (IOException e) {
            state.removeEmitter(emitter);
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public void publish(String sessionId, String eventName, String data) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            return;
        }
        state.send(eventName, data);
    }

    public static class SessionState {
        private final String sessionId;
        private final String serverCode;
        private final String protocolVersion;
        private volatile Instant expiresAt;
        private final List<SseEmitter> emitters = new ArrayList<>();

        private SessionState(String sessionId, String serverCode, String protocolVersion, Instant expiresAt) {
            this.sessionId = sessionId;
            this.serverCode = serverCode;
            this.protocolVersion = protocolVersion;
            this.expiresAt = expiresAt;
        }

        public String sessionId() {
            return sessionId;
        }

        public String serverCode() {
            return serverCode;
        }

        public String protocolVersion() {
            return protocolVersion;
        }

        private boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }

        private void touch() {
            expiresAt = Instant.now().plus(SESSION_TTL);
        }

        private synchronized void addEmitter(SseEmitter emitter) {
            emitters.add(emitter);
        }

        private synchronized void removeEmitter(SseEmitter emitter) {
            emitters.remove(emitter);
        }

        private synchronized void completeEmitters() {
            for (SseEmitter emitter : List.copyOf(emitters)) {
                emitter.complete();
            }
            emitters.clear();
        }

        private synchronized void send(String eventName, String data) {
            for (SseEmitter emitter : List.copyOf(emitters)) {
                try {
                    emitter.send(SseEmitter.event().name(eventName).data(data));
                } catch (IOException e) {
                    emitters.remove(emitter);
                    emitter.completeWithError(e);
                }
            }
        }
    }
}
