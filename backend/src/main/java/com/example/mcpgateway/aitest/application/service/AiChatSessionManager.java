package com.example.mcpgateway.aitest.application.service;

import com.example.mcpgateway.aitest.domain.model.AiModelConfig;
import com.example.mcpgateway.aitest.domain.repository.AiModelConfigRepository;
import com.example.mcpgateway.apitool.domain.model.HttpTool;
import com.example.mcpgateway.apitool.domain.model.ParameterMapping;
import com.example.mcpgateway.apitool.domain.repository.HttpToolRepository;
import com.example.mcpgateway.apitool.domain.repository.ParameterMappingRepository;
import com.example.mcpgateway.common.crypto.EncryptionService;
import com.example.mcpgateway.executor.HttpToolDefinition;
import com.example.mcpgateway.executor.HttpToolExecutor;
import com.example.mcpgateway.gateway.application.service.McpServerProvider;
import com.example.mcpgateway.gateway.domain.model.PublishedServer;
import com.example.mcpgateway.gateway.domain.model.PublishedTool;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 */
@Service
public class AiChatSessionManager {

    private static final Logger log = LoggerFactory.getLogger(AiChatSessionManager.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final McpServerProvider serverProvider;
    private final AiModelConfigRepository configRepo;
    private final EncryptionService encryption;
    private final HttpToolRepository httpTools;
    private final ParameterMappingRepository paramMappings;
    private final HttpToolExecutor executor;

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public AiChatSessionManager(McpServerProvider serverProvider, AiModelConfigRepository configRepo,
                                 EncryptionService encryption, HttpToolRepository httpTools,
                                 ParameterMappingRepository paramMappings, HttpToolExecutor executor) {
        this.serverProvider = serverProvider;
        this.configRepo = configRepo;
        this.encryption = encryption;
        this.httpTools = httpTools;
        this.paramMappings = paramMappings;
        this.executor = executor;
    }

    public SessionInfo startSession(long serverId, long modelConfigId) {
        // 1. Load server
        PublishedServer server = serverProvider.loadById(serverId)
                .orElseThrow(() -> new IllegalArgumentException("Server not found or not published: " + serverId));

        // 2. Load model config
        AiModelConfig config = configRepo.findById(modelConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Model config not found: " + modelConfigId));
        String apiKey = encryption.decrypt(config.apiKeyEnc());

        // 3. Create session
        String sessionId = UUID.randomUUID().toString();
        List<String> toolNames = server.tools().stream().map(PublishedTool::name).toList();

        ChatSession session = new ChatSession(sessionId, server, config, apiKey);
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

                    // Parse arguments
                    JsonNode args = mapper.readTree(argumentsJson);
                    Map<String, Object> paramValues = new HashMap<>();
                    args.fieldNames().forEachRemaining(key -> {
                        JsonNode val = args.get(key);
                        if (val.isTextual()) paramValues.put(key, val.asText());
                        else if (val.isNumber()) paramValues.put(key, val.numberValue());
                        else if (val.isBoolean()) paramValues.put(key, val.booleanValue());
                        else if (!val.isNull()) paramValues.put(key, val.toString());
                    });

                    // Execute tool
                    ToolCallInfo result = executeToolCall(session, originalName, paramValues, callId);
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

    private ToolCallInfo executeToolCall(ChatSession session, String toolName,
                                          Map<String, Object> paramValues, String callId) {
        PublishedTool tool = session.server.tools().stream()
                .filter(t -> t.name().equals(toolName))
                .findFirst().orElse(null);
        if (tool == null) {
            return new ToolCallInfo(toolName, paramValues, null, false, 0, "Tool not found: " + toolName);
        }

        try {
            HttpTool httpTool = httpTools.findById(tool.id())
                    .orElseThrow(() -> new RuntimeException("Tool DB record not found: " + tool.id()));

            List<ParameterMapping> mappings = paramMappings.findByToolId(httpTool.id());
            List<HttpToolDefinition.ParameterMapping> defMappings = mappings.stream()
                    .map(pm -> new HttpToolDefinition.ParameterMapping(
                            pm.name(), pm.paramSource().name(), pm.paramLocation(),
                            resolveType(pm.schemaJson()), pm.required(), pm.description()))
                    .toList();

            HttpToolDefinition definition = new HttpToolDefinition(
                    httpTool.httpMethod().name(), httpTool.urlTemplate(),
                    httpTool.headers(), defMappings);

            var result = executor.execute(definition, paramValues);

            String resultText;
            if (result.success()) {
                resultText = result.responseSummary() != null ? result.responseSummary().body() : "(empty response)";
            } else {
                resultText = "Error: " + (result.errorMessage() != null ? result.errorMessage() : "Unknown error");
            }

            return new ToolCallInfo(toolName, paramValues, resultText, result.success(),
                    result.statusCode(), result.errorMessage());
        } catch (Exception e) {
            return new ToolCallInfo(toolName, paramValues, null, false, 0, e.getMessage());
        }
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

    private String resolveType(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return "string";
        try {
            var node = mapper.readTree(schemaJson);
            return node.has("type") ? node.get("type").asText() : "string";
        } catch (Exception e) {
            return "string";
        }
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
        final Map<String, String> fnNameMap = new HashMap<>(); // sanitized -> original
        final List<ObjectNode> messages = new ArrayList<>();

        ChatSession(String sessionId, PublishedServer server, AiModelConfig config, String apiKey) {
            this.sessionId = sessionId;
            this.server = server;
            this.baseUrl = config.baseUrl();
            this.apiKey = apiKey;
            this.model = config.model();
        }
    }
}
