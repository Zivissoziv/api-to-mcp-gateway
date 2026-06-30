package com.example.mcpgateway.gateway.controller;

import com.example.mcpgateway.gateway.application.service.McpProtocolService;
import com.example.mcpgateway.gateway.application.service.McpStreamableHttpSessionRegistry;
import com.example.mcpgateway.gateway.application.service.McpStreamableHttpSessionRegistry.SessionState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Locale;

@RestController
@RequestMapping("/mcp/{serverCode}")
public class McpGatewayController {

    public static final String HEADER_SESSION_ID = "Mcp-Session-Id";
    public static final String HEADER_PROTOCOL_VERSION = "MCP-Protocol-Version";

    private static final ObjectMapper mapper = new ObjectMapper();

    private final McpProtocolService protocolService;
    private final McpStreamableHttpSessionRegistry sessions;

    public McpGatewayController(McpProtocolService protocolService,
                                McpStreamableHttpSessionRegistry sessions) {
        this.protocolService = protocolService;
        this.sessions = sessions;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<?> post(@PathVariable String serverCode,
                           @RequestBody String rawBody,
                           @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
                           @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
                           @RequestHeader(value = HEADER_SESSION_ID, required = false) String sessionId,
                           @RequestHeader(value = HEADER_PROTOCOL_VERSION, required = false) String protocolVersion,
                           @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
                           HttpServletRequest request) {
        if (!isJsonContent(contentType)) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body("Content-Type must be application/json");
        }
        if (!acceptsJson(accept) && !acceptsSse(accept)) {
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                    .body("Accept must include application/json or text/event-stream");
        }

        if (!isInitializeRequest(rawBody) && !isInitializedNotification(rawBody)
                && (sessionId == null || sessionId.isBlank())) {
            return ResponseEntity.badRequest().body("Missing MCP session. Initialize first and include Mcp-Session-Id.");
        }
        if (!isInitializeRequest(rawBody) && !isInitializedNotification(rawBody)
                && sessions.find(sessionId, serverCode).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Invalid or expired MCP session");
        }

        McpProtocolService.ProtocolResult result =
                protocolService.handle(serverCode, rawBody, authHeader, protocolVersion, request);

        String responseProtocolVersion = result.protocolVersion();
        String responseSessionId = sessionId;
        if (result.initialized()) {
            SessionState state = sessions.create(serverCode, responseProtocolVersion);
            responseSessionId = state.sessionId();
        }

        if (result.response() == null) {
            String finalResponseSessionId = responseSessionId;
            return ResponseEntity.accepted()
                    .header(HEADER_PROTOCOL_VERSION, responseProtocolVersion)
                    .headers(headers -> addSessionHeader(headers, finalResponseSessionId))
                    .build();
        }

        if (acceptsSse(accept) && prefersSse(accept)) {
            SseEmitter emitter = new SseEmitter(0L);
            try {
                String responseJson = mapper.writeValueAsString(result.response());
                emitter.send(SseEmitter.event().name("message").data(responseJson));
                if (responseSessionId != null && !responseSessionId.isBlank()) {
                    sessions.publish(responseSessionId, "message", responseJson);
                }
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            }
            String finalResponseSessionId = responseSessionId;
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header(HEADER_PROTOCOL_VERSION, responseProtocolVersion)
                    .headers(headers -> addSessionHeader(headers, finalResponseSessionId))
                    .body(emitter);
        }

        String finalResponseSessionId = responseSessionId;
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_PROTOCOL_VERSION, responseProtocolVersion)
                .headers(headers -> addSessionHeader(headers, finalResponseSessionId))
                .body(result.response());
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable String serverCode,
                      @RequestHeader(value = HEADER_SESSION_ID, required = false) String sessionId) {
        try {
            return sessions.subscribe(sessionId, serverCode);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage(), e);
        }
    }

    @DeleteMapping
    ResponseEntity<Void> delete(@PathVariable String serverCode,
                                @RequestHeader(value = HEADER_SESSION_ID, required = false) String sessionId) {
        if (sessions.find(sessionId, serverCode).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        sessions.close(sessionId);
        return ResponseEntity.noContent().build();
    }

    private boolean isJsonContent(String contentType) {
        return contentType != null && contentType.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean acceptsJson(String accept) {
        return accept == null || accept.isBlank()
                || accept.contains(MediaType.ALL_VALUE)
                || accept.toLowerCase(Locale.ROOT).contains(MediaType.APPLICATION_JSON_VALUE);
    }

    private boolean acceptsSse(String accept) {
        return accept != null && accept.toLowerCase(Locale.ROOT).contains(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private boolean prefersSse(String accept) {
        return accept != null && accept.toLowerCase(Locale.ROOT).trim().startsWith(MediaType.TEXT_EVENT_STREAM_VALUE);
    }

    private void addSessionHeader(HttpHeaders headers, String sessionId) {
        if (sessionId != null && !sessionId.isBlank()) {
            headers.add(HEADER_SESSION_ID, sessionId);
        }
    }

    private boolean isInitializeRequest(String rawBody) {
        return hasMethod(rawBody, "initialize");
    }

    private boolean isInitializedNotification(String rawBody) {
        return hasMethod(rawBody, "notifications/initialized");
    }

    private boolean hasMethod(String rawBody, String method) {
        try {
            JsonNode root = mapper.readTree(rawBody);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    if (item.has("method") && method.equals(item.get("method").asText())) {
                        return true;
                    }
                }
                return false;
            }
            return root.has("method") && method.equals(root.get("method").asText());
        } catch (Exception e) {
            return false;
        }
    }
}
