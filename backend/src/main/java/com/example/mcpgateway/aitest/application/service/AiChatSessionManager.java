package com.example.mcpgateway.aitest.application.service;

import com.example.mcpgateway.aitest.domain.model.AiModelConfig;
import com.example.mcpgateway.aitest.domain.repository.AiModelConfigRepository;
import com.example.mcpgateway.common.crypto.EncryptionService;
import com.example.mcpgateway.gateway.application.service.McpServerProvider;
import com.example.mcpgateway.gateway.domain.model.PublishedServer;
import com.example.mcpgateway.gateway.domain.model.PublishedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages AI chat sessions. Each session pairs a PublishedServer's tools
 * with an OpenAI-compatible chat model for natural-language testing.
 *
 * Tool invocations go through the real MCP gateway HTTP endpoint (/mcp/{serverCode})
 * so they are recorded in gateway_calls and tested exactly as a real client would use them.
 */
@Service
public class AiChatSessionManager {

    private static final Logger log = LoggerFactory.getLogger(AiChatSessionManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final McpServerProvider serverProvider;
    private final AiModelConfigRepository configRepo;
    private final EncryptionService encryption;
    private final RestClient httpClient;

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public AiChatSessionManager(McpServerProvider serverProvider, AiModelConfigRepository configRepo,
                                 EncryptionService encryption) {
        this.serverProvider = serverProvider;
        this.configRepo = configRepo;
        this.encryption = encryption;
        this.httpClient = RestClient.builder().build();
    }

    public SessionInfo startSession(long serverId, long modelConfigId, String mcpKey) {
        // 1. Load server
        PublishedServer server = serverProvider.loadById(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found or not published: " + serverId));

        // 2. Load model config
        AiModelConfig config = configRepo.findById(modelConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Model config not found: " + modelConfigId));
        String apiKey = encryption.decrypt(config.apiKeyEnc());

        // 3. Use user-provided MCP key to authenticate to the gateway
        log.info("AI chat session start: serverId={} serverCode={} hasMcpKey={}", serverId, server.code(),
                mcpKey != null && !mcpKey.isBlank());

        // 4. Create session
        String sessionId = UUID.randomUUID().toString();
        List<String> toolNames = server.tools().stream().map(PublishedTool::name).toList();

        ChatSession session = new ChatSession(sessionId, server, config, apiKey, mcpKey);
        sessions.put(sessionId, session);

        return new SessionInfo(sessionId, server.name(), server.code(), toolNames);
    }

    public ChatReply sendMessage(String sessionId, String userMessage) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) throw new IllegalArgumentException("Session not found: " + sessionId);

        long start = System.currentTimeMillis();

        try {
            // 1. Add user message to history
            session.messages.add(message("user", userMessage));

            // 2. Build OpenAI-compatible request
            ObjectNode requestBody = buildChatRequest(session);

            // 3. Send to OpenAI-compatible API
            String responseBody = sendRequest(session, requestBody);

            // 4. Parse response
            JsonNode response = mapper.readTree(responseBody);
            JsonNode choice = response.get("choices").get(0);
            JsonNode messageNode = choice.get("message");

            // 5. Check for function/tool call
            String content = messageNode.has("content") && !messageNode.get("content").isNull()
                    ? messageNode.get("content").asText() : "";

            List<ToolCallInfo> toolCalls = new ArrayList<>();

            if (messageNode.has("tool_calls") && !messageNode.get("tool_calls").isNull()) {
                // Add assistant message with tool_calls to history first
                session.messages.add((ObjectNode) messageNode);

                for (JsonNode tc : messageNode.get("tool_calls")) {
                    String fnName = tc.get("function").get("name").asText();
                    // Resolve sanitized name back to original
                    String originalName = session.fnNameMap.getOrDefault(fnName, fnName);
                    String argumentsJson = tc.get("function").get("arguments").asText();
                    String callId = tc.get("id").asText();

                    // Execute tool via real MCP gateway HTTP endpoint
                    ToolCallInfo result = executeToolCallViaGateway(session, originalName, argumentsJson);
                    toolCalls.add(result);

                    // Add tool result to messages
                    ObjectNode toolResultMsg = mapper.createObjectNode();
                    toolResultMsg.put("role", "tool");
                    toolResultMsg.put("tool_call_id", callId);
                    toolResultMsg.put("content", result.resultText());
                    session.messages.add(toolResultMsg);
                }

                // 6. If we executed tools, get the final response from the model
                if (!toolCalls.isEmpty()) {
                    ObjectNode followUpRequest = buildChatRequest(session);
                    String followUpBody = sendRequest(session, followUpRequest);
                    JsonNode followUpResponse = mapper.readTree(followUpBody);
                    JsonNode followUpChoice = followUpResponse.get("choices").get(0);
                    JsonNode followUpMsg = followUpChoice.get("message");
                    if (followUpMsg.has("content") && !followUpMsg.get("content").isNull()) {
                        content = followUpMsg.get("content").asText();
                    }
                    session.messages.add(message("assistant", content));
                }
            } else {
                session.messages.add(message("assistant", content));
            }

            long durationMs = System.currentTimeMillis() - start;
            return new ChatReply(content, toolCalls, durationMs);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            log.error("AI chat error: session={}", sessionId, e);
            return new ChatReply("Error: " + e.getMessage(), List.of(), durationMs);
        }
    }

    public void closeSession(String sessionId) {
        sessions.remove(sessionId);
    }

    /**
     * Execute an MCP tool call through the real MCP gateway HTTP endpoint,
     * exactly as a real MCP client would. This ensures the call is recorded
     * in gateway_calls and exercises the full production code path.
     */
    private ToolCallInfo executeToolCallViaGateway(ChatSession session, String toolName, String argumentsJson) {
        if (session.mcpKey == null || session.mcpKey.isBlank()) {
            log.warn("No MCP key available for server={}, cannot call gateway", session.server.code());
            return new ToolCallInfo(toolName, Map.of(), "Error: MCP key not available", false, 0, "MCP key not available");
        }

        try {
            // Build the JSON-RPC tools/call request body
            ObjectNode jsonRpcRequest = mapper.createObjectNode();
            jsonRpcRequest.put("jsonrpc", "2.0");
            jsonRpcRequest.put("id", "ai-" + UUID.randomUUID().toString().substring(0, 8));
            jsonRpcRequest.put("method", "tools/call");
            ObjectNode params = jsonRpcRequest.putObject("params");
            params.put("name", toolName);
            try {
                JsonNode args = mapper.readTree(argumentsJson);
                params.set("arguments", args);
            } catch (Exception e) {
                params.putObject("arguments");
            }

            String requestJson = mapper.writeValueAsString(jsonRpcRequest);
            String mcpUrl = "http://localhost:" + guessPort() + "/mcp/" + session.server.code();

            log.info("→ MCP gateway POST {} tool={}", mcpUrl, toolName);

            // POST to local MCP gateway endpoint — use exchange() to capture non-2xx responses
            var responseEntity = httpClient.post()
                    .uri(mcpUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + session.mcpKey)
                    .body(requestJson)
                    .retrieve()
                    .toEntity(String.class);

            int httpStatus = responseEntity.getStatusCode().value();
            String responseJson = responseEntity.getBody();

            log.info("← MCP gateway HTTP {} tool={} bodyLength={}", httpStatus, toolName,
                    responseJson != null ? responseJson.length() : 0);

            if (responseJson == null || responseJson.isBlank()) {
                return new ToolCallInfo(toolName, Map.of(), "Error: empty response from gateway", false, httpStatus, "Empty response");
            }

            // Parse JSON-RPC response
            JsonNode response = mapper.readTree(responseJson);
            if (response.has("error")) {
                JsonNode err = response.get("error");
                String errMsg = err.has("message") ? err.get("message").asText() : "MCP error";
                log.warn("← MCP gateway error tool={} code={} msg={}", toolName,
                        err.has("code") ? err.get("code").asInt() : -1, errMsg);
                return new ToolCallInfo(toolName, Map.of(), "Error: " + errMsg, false, 0, errMsg);
            }

            // Extract text content from result.content[]
            JsonNode result = response.get("result");
            String text = "";
            if (result != null && result.has("content") && result.get("content").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode item : result.get("content")) {
                    JsonNode type = item.get("type");
                    if (type != null && "text".equals(type.asText())) {
                        sb.append(item.has("text") ? item.get("text").asText() : "");
                    }
                }
                text = sb.toString();
            }

            boolean isError = result != null && result.has("isError") && result.get("isError").asBoolean();
            if (!isError) {
                log.info("← MCP gateway success tool={} resultLength={}", toolName, text.length());
            }
            return new ToolCallInfo(toolName, Map.of(), text, !isError, isError ? -1 : 0, isError ? "MCP error" : null);

        } catch (Exception e) {
            log.error("← MCP gateway exception server={} tool={}: {}", session.server.code(), toolName, e.toString());
            return new ToolCallInfo(toolName, Map.of(), null, false, 0, e.getMessage());
        }
    }

    private int guessPort() {
        String port = System.getProperty("server.port");
        if (port != null) return Integer.parseInt(port);
        port = System.getenv("SERVER_PORT");
        if (port != null) return Integer.parseInt(port);
        return 8080;
    }

    private ObjectNode buildChatRequest(ChatSession session) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", session.model);
        body.put("temperature", 0.7);

        ArrayNode messagesArray = body.putArray("messages");
        messagesArray.addAll(session.messages);

        // Add system message as first if not present
        if (session.messages.isEmpty() || !session.messages.get(0).get("role").asText().equals("system")) {
            ObjectNode sysMsg = mapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", "You are a helpful assistant with access to API tools. Use them when appropriate to answer the user's questions. Describe what you found.");
            messagesArray.insert(0, sysMsg);
        }

        // Add tool definitions
        ArrayNode toolsArray = body.putArray("tools");
        for (PublishedTool tool : session.server.tools()) {
            String fnName = sanitizeFnName(tool.name());
            session.fnNameMap.put(fnName, tool.name());

            ObjectNode toolDef = toolsArray.addObject();
            toolDef.put("type", "function");
            ObjectNode function = toolDef.putObject("function");
            function.put("name", fnName);
            function.put("description", tool.description() != null ? tool.description() : "");
            try {
                JsonNode schema = mapper.readTree(tool.inputSchema());
                function.set("parameters", schema);
            } catch (Exception e) {
                function.set("parameters", defaultSchema());
            }
        }

        return body;
    }

    private ObjectNode defaultSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.putObject("properties");
        schema.putArray("required");
        return schema;
    }

    private ObjectNode message(String role, String content) {
        ObjectNode msg = mapper.createObjectNode();
        msg.put("role", role);
        msg.put("content", content);
        return msg;
    }

    private String sendRequest(ChatSession session, ObjectNode requestBody) {
        try {
            RestClient client = RestClient.builder()
                    .baseUrl(session.baseUrl + "/chat/completions")
                    .defaultHeader("Authorization", "Bearer " + session.apiKey)
                    .defaultHeader("Content-Type", "application/json")
                    .build();

            String json = mapper.writeValueAsString(requestBody);
            return client.post()
                    .body(json)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            throw new RuntimeException("AI model request failed: " + e.getMessage());
        }
    }

    private String sanitizeFnName(String name) {
        // OpenAI requires function names to match ^[a-zA-Z0-9_-]+$
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    // -- Data types --

    public record SessionInfo(String sessionId, String serverName, String serverCode, List<String> tools) {}

    public record ChatReply(String reply, List<ToolCallInfo> toolCalls, long durationMs) {}

    public record ToolCallInfo(String toolName, Map<String, Object> params, String resultText,
                                boolean success, int statusCode, String error) {}

    private static class ChatSession {
        final String sessionId;
        final PublishedServer server;
        final String baseUrl;
        final String apiKey;
        final String model;
        final String mcpKey;
        final Map<String, String> fnNameMap = new HashMap<>(); // sanitized -> original
        final List<ObjectNode> messages = new ArrayList<>();

        ChatSession(String sessionId, PublishedServer server, AiModelConfig config, String apiKey, String mcpKey) {
            this.sessionId = sessionId;
            this.server = server;
            this.baseUrl = config.baseUrl();
            this.apiKey = apiKey;
            this.model = config.model();
            this.mcpKey = mcpKey;
        }
    }
}
