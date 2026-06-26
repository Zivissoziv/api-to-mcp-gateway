package com.example.mcpgateway.gateway.controller;

import com.example.mcpgateway.apitool.domain.model.HttpTool;
import com.example.mcpgateway.apitool.domain.model.ParameterMapping;
import com.example.mcpgateway.apitool.domain.repository.HttpToolRepository;
import com.example.mcpgateway.apitool.domain.repository.ParameterMappingRepository;
import com.example.mcpgateway.executor.ExecutionResult;
import com.example.mcpgateway.executor.HttpToolDefinition;
import com.example.mcpgateway.executor.HttpToolExecutor;
import com.example.mcpgateway.gateway.application.service.McpServerAuthVerifier;
import com.example.mcpgateway.gateway.application.service.McpServerProvider;
import com.example.mcpgateway.gateway.application.service.GatewayCallRecorder;
import com.example.mcpgateway.gateway.domain.model.PublishedServer;
import com.example.mcpgateway.gateway.domain.model.PublishedTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/mcp/{serverCode}")
public class McpGatewayController {

    private static final Logger log = LoggerFactory.getLogger(McpGatewayController.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String PROTOCOL_VERSION = "2025-03-26";
    private static final String JSON_RPC_VERSION = "2.0";

    private final McpServerProvider serverProvider;
    private final McpServerAuthVerifier authVerifier;
    private final HttpToolRepository httpTools;
    private final ParameterMappingRepository mappings;
    private final HttpToolExecutor executor;
    private final GatewayCallRecorder callRecorder;

    public McpGatewayController(McpServerProvider serverProvider, McpServerAuthVerifier authVerifier,
                                 HttpToolRepository httpTools,
                                 ParameterMappingRepository mappings,
                                 HttpToolExecutor executor, GatewayCallRecorder callRecorder) {
        this.serverProvider = serverProvider;
        this.authVerifier = authVerifier;
        this.httpTools = httpTools;
        this.mappings = mappings;
        this.executor = executor;
        this.callRecorder = callRecorder;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> handle(@PathVariable String serverCode,
                                  @RequestBody String rawBody,
                                  @RequestHeader(value = "Authorization", required = false) String authHeader,
                                  HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        String clientIp = request.getRemoteAddr();

        // 1. Parse JSON-RPC body
        JsonNode jsonRpc;
        try {
            jsonRpc = mapper.readTree(rawBody);
        } catch (Exception e) {
            return jsonRpcError(null, -32700, "Parse error");
        }

        String jsonrpc = jsonRpc.has("jsonrpc") ? jsonRpc.get("jsonrpc").asText() : JSON_RPC_VERSION;
        JsonNode id = jsonRpc.get("id");
        String method = jsonRpc.has("method") ? jsonRpc.get("method").asText() : "";
        JsonNode params = jsonRpc.has("params") ? jsonRpc.get("params") : mapper.createObjectNode();

        if (!JSON_RPC_VERSION.equals(jsonrpc)) {
            return jsonRpcError(id, -32600, "Invalid Request: jsonrpc version must be 2.0");
        }
        if (method.isBlank()) {
            return jsonRpcError(id, -32600, "Invalid Request: method is required");
        }

        // 2. Load published server
        PublishedServer server = serverProvider.load(serverCode).orElse(null);
        if (server == null) {
            return jsonRpcError(id, -32002, "Server not found or not published");
        }

        // 3. Verify MCP Key (skip for initialize — client may not have key yet)
        if (!"initialize".equals(method)) {
            String presentedKey = null;
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                presentedKey = authHeader.substring(7);
            }
            if (!authVerifier.verify(server.id(), presentedKey)) {
                return jsonRpcError(id, -32001, "Invalid or missing MCP Key");
            }
        }

        // 4. Route
        long startNanos = System.nanoTime();
        try {
            return switch (method) {
                case "initialize" -> handleInitialize(id, server);
                case "tools/list" -> handleToolsList(id, server, traceId, clientIp);
                case "tools/call" -> handleToolsCall(id, server, params, traceId, clientIp, startNanos);
                case "ping" -> handlePing(id);
                default -> jsonRpcError(id, -32601, "Method not found: " + method);
            };
        } catch (Exception e) {
            log.error("MCP request error: server={} method={}", serverCode, method, e);
            return jsonRpcError(id, -32603, "Internal error");
        }
    }

    private ResponseEntity<String> handleInitialize(JsonNode id, PublishedServer server) {
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", PROTOCOL_VERSION);
        ObjectNode capabilities = result.putObject("capabilities");
        capabilities.putObject("tools");
        ObjectNode serverInfo = result.putObject("serverInfo");
        serverInfo.put("name", server.name());
        serverInfo.put("version", "1.0.0");
        return jsonRpcSuccess(id, result);
    }

    private ResponseEntity<String> handleToolsList(JsonNode id, PublishedServer server,
                                                     String traceId, String clientIp) {
        callRecorder.record(server.code(), null, clientIp, traceId, "tools/list", true, 0, 0, null);
        ObjectNode result = mapper.createObjectNode();
        var toolsArray = result.putArray("tools");
        for (PublishedTool tool : server.tools()) {
            ObjectNode t = toolsArray.addObject();
            t.put("name", tool.name());
            t.put("description", tool.description() != null ? tool.description() : "");
            try {
                JsonNode schema = mapper.readTree(tool.inputSchema());
                t.set("inputSchema", schema);
            } catch (Exception e) {
                t.set("inputSchema", mapper.createObjectNode());
            }
        }
        return jsonRpcSuccess(id, result);
    }

    private ResponseEntity<String> handleToolsCall(JsonNode id, PublishedServer server,
                                                     JsonNode params, String traceId,
                                                     String clientIp, long startNanos) {
        String toolName = params.has("name") ? params.get("name").asText() : "";
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        // Find tool in published server and get its DB ID
        PublishedTool publishedTool = server.tools().stream()
                .filter(t -> t.name().equals(toolName))
                .findFirst()
                .orElse(null);
        if (publishedTool == null) {
            callRecorder.record(server.code(), toolName, clientIp, traceId, "tools/call",
                    false, 0, 0, "Tool not found: " + toolName);
            return jsonRpcError(id, -32003, "Tool not found: " + toolName);
        }

        // Load full HTTP tool definition from DB
        HttpTool httpTool = httpTools.findById(publishedTool.id()).orElse(null);
        if (httpTool == null) {
            return jsonRpcError(id, -32004, "Tool definition not found in database");
        }

        try {
            // Build parameter map from MCP arguments
            Map<String, Object> paramValues = new HashMap<>();
            if (arguments.isObject()) {
                arguments.fieldNames().forEachRemaining(key -> {
                    JsonNode val = arguments.get(key);
                    if (val.isTextual()) paramValues.put(key, val.asText());
                    else if (val.isNumber()) paramValues.put(key, val.numberValue());
                    else if (val.isBoolean()) paramValues.put(key, val.booleanValue());
                    else if (!val.isNull()) paramValues.put(key, val.asText());
                });
            }

            // Build HttpToolDefinition
            List<ParameterMapping> paramMappings = mappings.findByToolId(httpTool.id());
            List<HttpToolDefinition.ParameterMapping> defMappings = paramMappings.stream()
                    .map(pm -> new HttpToolDefinition.ParameterMapping(
                            pm.name(), pm.paramSource().name(), pm.paramLocation(),
                            resolveType(pm.schemaJson()), pm.required(), pm.description()))
                    .toList();

            HttpToolDefinition definition = new HttpToolDefinition(
                    httpTool.httpMethod().name(),
                    httpTool.urlTemplate(),
                    httpTool.headers(),
                    httpTool.bodyTemplate(),
                    defMappings);

            // Execute
            ExecutionResult result = executor.execute(definition, paramValues);
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;

            callRecorder.record(server.code(), toolName, clientIp, traceId, "tools/call",
                    result.success(), result.statusCode(), (int) durationMs, result.errorMessage());

            // Convert to MCP response
            ObjectNode resultObj = mapper.createObjectNode();
            var content = resultObj.putArray("content");
            ObjectNode textContent = content.addObject();
            textContent.put("type", "text");
            if (result.success()) {
                String body = result.responseSummary() != null ? result.responseSummary().body() : "";
                textContent.put("text", body);
            } else {
                String errMsg = result.errorMessage() != null ? result.errorMessage() : "Unknown error";
                textContent.put("text", "Error: " + errMsg);
                resultObj.put("isError", true);
            }

            return jsonRpcSuccess(id, resultObj);

        } catch (Exception e) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            callRecorder.record(server.code(), toolName, clientIp, traceId, "tools/call",
                    false, 0, (int) durationMs, e.getMessage());
            return jsonRpcError(id, -32004, "Tool execution error");
        }
    }

    private ResponseEntity<String> handlePing(JsonNode id) {
        return jsonRpcSuccess(id, mapper.createObjectNode());
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

    // -- JSON-RPC response helpers --

    private ResponseEntity<String> jsonRpcSuccess(JsonNode id, ObjectNode result) {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", JSON_RPC_VERSION);
        resp.set("id", id);
        resp.set("result", result);
        return ResponseEntity.ok(resp.toString());
    }

    private ResponseEntity<String> jsonRpcError(JsonNode id, int code, String message) {
        ObjectNode resp = mapper.createObjectNode();
        resp.put("jsonrpc", JSON_RPC_VERSION);
        resp.set("id", id);
        ObjectNode error = resp.putObject("error");
        error.put("code", code);
        error.put("message", message);
        return ResponseEntity.ok(resp.toString());
    }
}
