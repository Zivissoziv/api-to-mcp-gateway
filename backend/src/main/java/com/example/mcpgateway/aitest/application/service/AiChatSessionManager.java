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
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        return startSession(List.of(serverId), modelConfigId, Map.of(serverId, mcpKey != null ? mcpKey : ""));
    }

    public SessionInfo startSession(List<Long> serverIds, long modelConfigId, Map<Long, String> mcpKeys) {
        if (serverIds == null || serverIds.isEmpty()) {
            throw new IllegalArgumentException("At least one MCP Server is required");
        }

        List<PublishedServer> servers = serverIds.stream()
                .map(serverId -> serverProvider.loadById(serverId)
                        .orElseThrow(() -> new IllegalArgumentException("Server not found or not published: " + serverId)))
                .toList();

        AiModelConfig config = configRepo.findById(modelConfigId)
                .orElseThrow(() -> new IllegalArgumentException("Model config not found: " + modelConfigId));
        String apiKey = encryption.decrypt(config.apiKeyEnc());

        Map<Long, String> safeMcpKeys = mcpKeys != null ? mcpKeys : Map.of();
        Map<String, String> keysByServerCode = new HashMap<>();
        Map<String, String> sessionsByServerCode = new HashMap<>();
        for (PublishedServer server : servers) {
            String key = safeMcpKeys.get(server.id());
            keysByServerCode.put(server.code(), key);
            sessionsByServerCode.put(server.code(), initializeMcpSession(server));
            log.info("AI chat session start: serverId={} serverCode={} hasMcpKey={}",
                    server.id(), server.code(), key != null && !key.isBlank());
        }

        String sessionId = UUID.randomUUID().toString();
        List<LoadedServerInfo> loadedServers = servers.stream()
                .map(server -> new LoadedServerInfo(
                        server.name(),
                        server.code(),
                        server.tools().stream().map(tool -> exposedToolName(server, tool)).toList()))
                .toList();
        List<String> toolNames = loadedServers.stream().flatMap(server -> server.tools().stream()).toList();

        ChatSession session = new ChatSession(sessionId, servers, config, apiKey, keysByServerCode, sessionsByServerCode);
        sessions.put(sessionId, session);

        String serverName = String.join(", ", servers.stream().map(PublishedServer::name).toList());
        String serverCode = String.join(",", servers.stream().map(PublishedServer::code).toList());
        return new SessionInfo(sessionId, serverName, serverCode, toolNames, loadedServers);
    }

    public ChatReply sendMessage(String sessionId, String userMessage) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) throw new IllegalArgumentException("Session not found: " + sessionId);

        long start = System.currentTimeMillis();

        try {
            session.messages.add(message("user", userMessage));

            ObjectNode requestBody = buildChatRequest(session);
            String responseBody = sendRequest(session, requestBody);

            JsonNode response = mapper.readTree(responseBody);
            JsonNode choice = response.get("choices").get(0);
            JsonNode messageNode = choice.get("message");

            String content = messageNode.has("content") && !messageNode.get("content").isNull()
                    ? messageNode.get("content").asText() : "";

            List<ToolCallInfo> toolCalls = new ArrayList<>();

            if (messageNode.has("tool_calls") && !messageNode.get("tool_calls").isNull()) {
                session.messages.add((ObjectNode) messageNode);

                for (JsonNode tc : messageNode.get("tool_calls")) {
                    String fnName = tc.get("function").get("name").asText();
                    String argumentsJson = tc.get("function").get("arguments").asText();
                    String callId = tc.get("id").asText();

                    ToolCallInfo result = executeToolCallViaGateway(session, fnName, argumentsJson);
                    toolCalls.add(result);

                    ObjectNode toolResultMsg = mapper.createObjectNode();
                    toolResultMsg.put("role", "tool");
                    toolResultMsg.put("tool_call_id", callId);
                    toolResultMsg.put("content", result.resultText() != null ? result.resultText() : result.error());
                    session.messages.add(toolResultMsg);
                }

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
        ChatSession session = sessions.remove(sessionId);
        if (session != null) {
            for (var entry : session.mcpSessionsByServerCode.entrySet()) {
                closeMcpSession(entry.getKey(), entry.getValue());
            }
        }
    }

    private String initializeMcpSession(PublishedServer server) {
        try {
            ObjectNode jsonRpcRequest = mapper.createObjectNode();
            jsonRpcRequest.put("jsonrpc", "2.0");
            jsonRpcRequest.put("id", "init-" + UUID.randomUUID().toString().substring(0, 8));
            jsonRpcRequest.put("method", "initialize");
            ObjectNode params = jsonRpcRequest.putObject("params");
            params.put("protocolVersion", "2025-06-18");
            params.putObject("capabilities");
            ObjectNode clientInfo = params.putObject("clientInfo");
            clientInfo.put("name", "mcp-gateway-ai-chat");
            clientInfo.put("version", "1.0.0");

            var responseEntity = httpClient.post()
                    .uri(mcpUrl(server.code()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json, text/event-stream")
                    .header("MCP-Protocol-Version", "2025-06-18")
                    .body(mapper.writeValueAsString(jsonRpcRequest))
                    .retrieve()
                    .toEntity(String.class);

            String sessionId = responseEntity.getHeaders().getFirst("Mcp-Session-Id");
            if (sessionId == null || sessionId.isBlank()) {
                throw new IllegalStateException("MCP initialize did not return Mcp-Session-Id");
            }
            return sessionId;
        } catch (Exception e) {
            throw new IllegalStateException("MCP initialize failed for server " + server.code() + ": " + e.getMessage(), e);
        }
    }

    private void closeMcpSession(String serverCode, String mcpSessionId) {
        if (mcpSessionId == null || mcpSessionId.isBlank()) {
            return;
        }
        try {
            httpClient.delete()
                    .uri(mcpUrl(serverCode))
                    .header("Mcp-Session-Id", mcpSessionId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Failed to close MCP session server={} session={}: {}", serverCode, mcpSessionId, e.toString());
        }
    }

    private ToolCallInfo executeToolCallViaGateway(ChatSession session, String exposedToolName, String argumentsJson) {
        ToolRoute route = session.toolRoutes.get(exposedToolName);
        if (route == null) {
            log.warn("Tool route not found: {}", exposedToolName);
            return new ToolCallInfo(exposedToolName, Map.of(), "Error: tool route not found", false, 0, "Tool route not found");
        }

        String mcpKey = session.mcpKeysByServerCode.get(route.server().code());
        String mcpSessionId = session.mcpSessionsByServerCode.get(route.server().code());
        if (mcpKey == null || mcpKey.isBlank()) {
            log.warn("No MCP key available for server={}, cannot call gateway", route.server().code());
            return new ToolCallInfo(exposedToolName, Map.of(), "Error: MCP key not available", false, 0, "MCP key not available");
        }
        if (mcpSessionId == null || mcpSessionId.isBlank()) {
            log.warn("No MCP session available for server={}, cannot call gateway", route.server().code());
            return new ToolCallInfo(exposedToolName, Map.of(), "Error: MCP session not available", false, 0, "MCP session not available");
        }

        try {
            ObjectNode jsonRpcRequest = mapper.createObjectNode();
            jsonRpcRequest.put("jsonrpc", "2.0");
            jsonRpcRequest.put("id", "ai-" + UUID.randomUUID().toString().substring(0, 8));
            jsonRpcRequest.put("method", "tools/call");
            ObjectNode params = jsonRpcRequest.putObject("params");
            params.put("name", route.originalToolName());
            try {
                JsonNode args = mapper.readTree(argumentsJson);
                params.set("arguments", args);
            } catch (Exception e) {
                params.putObject("arguments");
            }

            String requestJson = mapper.writeValueAsString(jsonRpcRequest);
            String mcpUrl = mcpUrl(route.server().code());

            log.info("MCP gateway POST {} tool={}", mcpUrl, exposedToolName);

            var responseEntity = httpClient.post()
                    .uri(mcpUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json, text/event-stream")
                    .header("Authorization", "Bearer " + mcpKey)
                    .header("Mcp-Session-Id", mcpSessionId)
                    .header("MCP-Protocol-Version", "2025-06-18")
                    .body(requestJson)
                    .retrieve()
                    .toEntity(String.class);

            int httpStatus = responseEntity.getStatusCode().value();
            String responseJson = responseEntity.getBody();

            if (responseJson == null || responseJson.isBlank()) {
                return new ToolCallInfo(exposedToolName, Map.of(), "Error: empty response from gateway", false, httpStatus, "Empty response");
            }

            JsonNode response = mapper.readTree(extractJsonResponse(responseJson));
            if (response.has("error")) {
                JsonNode err = response.get("error");
                String errMsg = err.has("message") ? err.get("message").asText() : "MCP error";
                log.warn("MCP gateway error tool={} code={} msg={}", exposedToolName,
                        err.has("code") ? err.get("code").asInt() : -1, errMsg);
                return new ToolCallInfo(exposedToolName, Map.of(), "Error: " + errMsg, false, 0, errMsg);
            }

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
            return new ToolCallInfo(exposedToolName, Map.of(), text, !isError, isError ? -1 : 0, isError ? "MCP error" : null);

        } catch (Exception e) {
            log.error("MCP gateway exception server={} tool={}: {}", route.server().code(), exposedToolName, e.toString());
            return new ToolCallInfo(exposedToolName, Map.of(), null, false, 0, e.getMessage());
        }
    }

    private int guessPort() {
        String port = System.getProperty("server.port");
        if (port != null) return Integer.parseInt(port);
        port = System.getenv("SERVER_PORT");
        if (port != null) return Integer.parseInt(port);
        return 8080;
    }

    private String mcpUrl(String serverCode) {
        return "http://localhost:" + guessPort() + "/mcp/" + serverCode;
    }

    private String extractJsonResponse(String responseBody) {
        if (responseBody == null) {
            return "";
        }
        String trimmed = responseBody.trim();
        if (!trimmed.startsWith("data:") && !trimmed.contains("\ndata:")) {
            return trimmed;
        }
        StringBuilder json = new StringBuilder();
        for (String line : trimmed.split("\\R")) {
            if (line.startsWith("data:")) {
                json.append(line.substring(5).trim());
            }
        }
        return json.toString();
    }

    private ObjectNode buildChatRequest(ChatSession session) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", session.model);
        body.put("temperature", 0.7);

        ArrayNode messagesArray = body.putArray("messages");
        messagesArray.addAll(session.messages);

        if (session.messages.isEmpty() || !"system".equals(session.messages.get(0).get("role").asText())) {
            ObjectNode sysMsg = mapper.createObjectNode();
            sysMsg.put("role", "system");
            sysMsg.put("content", "You are a helpful assistant with access to API tools from one or more MCP servers. Tool names are namespaced as serverCode__toolName. Use them when appropriate to answer the user's questions. Describe what you found.");
            messagesArray.insert(0, sysMsg);
        }

        ArrayNode toolsArray = body.putArray("tools");
        for (PublishedServer server : session.servers) {
            for (PublishedTool tool : server.tools()) {
                String fnName = exposedToolName(server, tool);
                session.toolRoutes.put(fnName, new ToolRoute(server, tool.name()));

                ObjectNode toolDef = toolsArray.addObject();
                toolDef.put("type", "function");
                ObjectNode function = toolDef.putObject("function");
                function.put("name", fnName);
                String description = tool.description() != null ? tool.description() : "";
                function.put("description", "[" + server.name() + " / " + server.code() + "] " + description);
                try {
                    JsonNode schema = mapper.readTree(tool.inputSchema());
                    function.set("parameters", schema);
                } catch (Exception e) {
                    function.set("parameters", defaultSchema());
                }
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
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private String exposedToolName(PublishedServer server, PublishedTool tool) {
        return sanitizeFnName(server.code() + "__" + tool.name());
    }

    public record SessionInfo(String sessionId, String serverName, String serverCode,
                              List<String> tools, List<LoadedServerInfo> servers) {}

    public record LoadedServerInfo(String serverName, String serverCode, List<String> tools) {}

    public record ChatReply(String reply, List<ToolCallInfo> toolCalls, long durationMs) {}

    public record ToolCallInfo(String toolName, Map<String, Object> params, String resultText,
                               boolean success, int statusCode, String error) {}

    private static class ChatSession {
        final String sessionId;
        final List<PublishedServer> servers;
        final String baseUrl;
        final String apiKey;
        final String model;
        final Map<String, String> mcpKeysByServerCode;
        final Map<String, String> mcpSessionsByServerCode;
        final Map<String, ToolRoute> toolRoutes = new HashMap<>();
        final List<ObjectNode> messages = new ArrayList<>();

        ChatSession(String sessionId, List<PublishedServer> servers, AiModelConfig config, String apiKey,
                    Map<String, String> mcpKeysByServerCode, Map<String, String> mcpSessionsByServerCode) {
            this.sessionId = sessionId;
            this.servers = servers;
            this.baseUrl = config.baseUrl();
            this.apiKey = apiKey;
            this.model = config.model();
            this.mcpKeysByServerCode = mcpKeysByServerCode;
            this.mcpSessionsByServerCode = mcpSessionsByServerCode;
        }
    }

    private record ToolRoute(PublishedServer server, String originalToolName) {}
}
