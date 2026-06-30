package com.example.mcpgateway.gateway.application.service;

import com.example.mcpgateway.apitool.domain.model.HttpTool;
import com.example.mcpgateway.apitool.domain.model.ParameterMapping;
import com.example.mcpgateway.apitool.domain.repository.HttpToolRepository;
import com.example.mcpgateway.apitool.domain.repository.ParameterMappingRepository;
import com.example.mcpgateway.executor.ExecutionResult;
import com.example.mcpgateway.executor.HttpToolDefinition;
import com.example.mcpgateway.executor.HttpToolExecutor;
import com.example.mcpgateway.gateway.domain.model.PublishedServer;
import com.example.mcpgateway.gateway.domain.model.PublishedTool;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import io.modelcontextprotocol.spec.McpSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class McpProtocolService {

    public static final String DEFAULT_PROTOCOL_VERSION = "2025-06-18";
    public static final String LEGACY_PROTOCOL_VERSION = "2025-03-26";
    public static final String JSON_RPC_VERSION = "2.0";

    private static final Logger log = LoggerFactory.getLogger(McpProtocolService.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper mapper;
    private final McpServerProvider serverProvider;
    private final McpServerAuthVerifier authVerifier;
    private final HttpToolRepository httpTools;
    private final ParameterMappingRepository mappings;
    private final HttpToolExecutor executor;
    private final GatewayCallRecorder callRecorder;

    public McpProtocolService(McpServerProvider serverProvider, McpServerAuthVerifier authVerifier,
                              HttpToolRepository httpTools, ParameterMappingRepository mappings,
                              HttpToolExecutor executor, GatewayCallRecorder callRecorder) {
        this.mapper = new ObjectMapper();
        this.serverProvider = serverProvider;
        this.authVerifier = authVerifier;
        this.httpTools = httpTools;
        this.mappings = mappings;
        this.executor = executor;
        this.callRecorder = callRecorder;
    }

    public ProtocolResult handle(String serverCode, String rawBody, String authHeader,
                                 String requestedProtocolVersion, HttpServletRequest request) {
        JsonNode root;
        try {
            root = mapper.readTree(rawBody);
        } catch (Exception e) {
            CallRecordContext ctx = new CallRecordContext(serverCode, clientIp(request), UUID.randomUUID().toString());
            ctx.method("parse");
            ctx.fail("PARSE_ERROR");
            publishCallRecord(ctx);
            return ProtocolResult.withResponse(jsonRpcError(null, -32700, "Parse error"), false,
                    negotiateProtocolVersion(requestedProtocolVersion));
        }

        String protocolVersion = negotiateProtocolVersion(extractRequestedProtocolVersion(root, requestedProtocolVersion));
        if (root.isArray()) {
            ArrayNode responses = mapper.createArrayNode();
            boolean initialized = false;
            for (JsonNode message : root) {
                SingleResult single = handleSingle(serverCode, message, authHeader, protocolVersion, request);
                initialized = initialized || single.initialized();
                if (single.response() != null) {
                    responses.add(single.response());
                }
            }
            JsonNode response = responses.isEmpty() ? null : responses;
            return ProtocolResult.withResponse(response, initialized, protocolVersion);
        }

        SingleResult single = handleSingle(serverCode, root, authHeader, protocolVersion, request);
        return ProtocolResult.withResponse(single.response(), single.initialized(), protocolVersion);
    }

    private SingleResult handleSingle(String serverCode, JsonNode jsonRpc, String authHeader,
                                      String protocolVersion, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString();
        CallRecordContext recordContext = new CallRecordContext(serverCode, clientIp(request), traceId);
        JsonNode id = jsonRpc.get("id");
        try {
            String jsonrpc = jsonRpc.has("jsonrpc") ? jsonRpc.get("jsonrpc").asText() : JSON_RPC_VERSION;
            String method = jsonRpc.has("method") ? jsonRpc.get("method").asText() : "";
            JsonNode params = jsonRpc.has("params") ? jsonRpc.get("params") : mapper.createObjectNode();
            recordContext.method(method);

            if (!JSON_RPC_VERSION.equals(jsonrpc)) {
                recordContext.fail("INVALID_JSONRPC_VERSION");
                return SingleResult.response(jsonRpcError(id, -32600, "Invalid Request: jsonrpc version must be 2.0"));
            }
            if (method.isBlank()) {
                recordContext.fail("MISSING_METHOD");
                return SingleResult.response(jsonRpcError(id, -32600, "Invalid Request: method is required"));
            }

            PublishedServer server = serverProvider.load(serverCode).orElse(null);
            if (server == null) {
                recordContext.fail("SERVER_NOT_FOUND");
                return SingleResult.response(jsonRpcError(id, -32002, "Server not found or not published"));
            }

            if (!"initialize".equals(method) && !McpSchema.METHOD_NOTIFICATION_INITIALIZED.equals(method)) {
                String presentedKey = bearerToken(authHeader);
                if (!authVerifier.verify(server.id(), presentedKey)) {
                    recordContext.fail("AUTH_FAILED");
                    return SingleResult.response(jsonRpcError(id, -32001, "Invalid or missing MCP Key"));
                }
            }

            return switch (method) {
                case McpSchema.METHOD_INITIALIZE -> {
                    recordContext.succeed();
                    yield SingleResult.initialized(jsonRpcSuccess(id, initializeResult(server, protocolVersion)));
                }
                case McpSchema.METHOD_NOTIFICATION_INITIALIZED -> {
                    recordContext.succeed();
                    yield SingleResult.noResponse();
                }
                case McpSchema.METHOD_TOOLS_LIST -> {
                    recordContext.succeed();
                    yield SingleResult.response(jsonRpcSuccess(id, toolsListResult(server)));
                }
                case McpSchema.METHOD_TOOLS_CALL -> SingleResult.response(
                        handleToolsCall(id, server, params, recordContext));
                case McpSchema.METHOD_PING -> {
                    recordContext.succeed();
                    yield SingleResult.response(jsonRpcSuccess(id, Map.of()));
                }
                default -> {
                    recordContext.fail("METHOD_NOT_FOUND");
                    yield SingleResult.response(jsonRpcError(id, -32601, "Method not found: " + method));
                }
            };
        } catch (Exception e) {
            log.error("MCP request error: server={} method={}", serverCode, recordContext.mcpMethod, e);
            recordContext.fail("INTERNAL_ERROR");
            return SingleResult.response(jsonRpcError(id, -32603, "Internal error"));
        } finally {
            publishCallRecord(recordContext);
        }
    }

    private McpSchema.InitializeResult initializeResult(PublishedServer server, String protocolVersion) {
        McpSchema.ServerCapabilities capabilities = McpSchema.ServerCapabilities.builder()
                .tools(true)
                .build();
        McpSchema.Implementation serverInfo = new McpSchema.Implementation(server.name(), "1.0.0");
        return new McpSchema.InitializeResult(protocolVersion, capabilities, serverInfo, null);
    }

    private McpSchema.ListToolsResult toolsListResult(PublishedServer server) {
        List<McpSchema.Tool> tools = server.tools().stream()
                .map(tool -> new McpSchema.Tool(tool.name(),
                        tool.description() != null ? tool.description() : "",
                        toJsonSchema(tool.inputSchema())))
                .toList();
        return new McpSchema.ListToolsResult(tools, null);
    }

    private JsonNode handleToolsCall(JsonNode id, PublishedServer server, JsonNode params,
                                     CallRecordContext recordContext) {
        String toolName = params.has("name") ? params.get("name").asText() : "";
        recordContext.tool(toolName);
        JsonNode arguments = params.has("arguments") ? params.get("arguments") : mapper.createObjectNode();

        PublishedTool publishedTool = server.tools().stream()
                .filter(t -> t.name().equals(toolName))
                .findFirst()
                .orElse(null);
        if (publishedTool == null) {
            recordContext.fail("TOOL_NOT_FOUND: " + toolName);
            return jsonRpcError(id, -32003, "Tool not found: " + toolName);
        }

        HttpTool httpTool = httpTools.findById(publishedTool.id()).orElse(null);
        if (httpTool == null) {
            recordContext.fail("TOOL_DEFINITION_NOT_FOUND: " + toolName);
            return jsonRpcError(id, -32004, "Tool definition not found in database");
        }

        try {
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

            ExecutionResult result = executor.execute(definition, paramValues);
            recordContext.result(result.success(), result.statusCode(), result.errorMessage());

            String text;
            boolean isError = !result.success();
            if (result.success()) {
                text = result.responseSummary() != null ? result.responseSummary().body() : "";
            } else {
                String errMsg = result.errorMessage() != null ? result.errorMessage() : "Unknown error";
                text = "Error: " + errMsg;
            }
            McpSchema.CallToolResult callToolResult = McpSchema.CallToolResult.builder()
                    .addTextContent(text)
                    .isError(isError)
                    .build();
            return jsonRpcSuccess(id, callToolResult);
        } catch (Exception e) {
            recordContext.fail(e.getMessage() != null ? e.getMessage() : "TOOL_EXECUTION_ERROR");
            return jsonRpcError(id, -32004, "Tool execution error");
        }
    }

    private McpSchema.JsonSchema toJsonSchema(String inputSchema) {
        try {
            JsonNode schema = mapper.readTree(inputSchema);
            String type = schema.has("type") ? schema.get("type").asText() : "object";
            Map<String, Object> properties = schema.has("properties") && schema.get("properties").isObject()
                    ? mapper.convertValue(schema.get("properties"), MAP_TYPE) : Map.of();
            List<String> required = schema.has("required") && schema.get("required").isArray()
                    ? mapper.convertValue(schema.get("required"), new TypeReference<>() {}) : List.of();
            Boolean additionalProperties = schema.has("additionalProperties")
                    ? schema.get("additionalProperties").asBoolean() : null;
            Map<String, Object> defs = schema.has("$defs") && schema.get("$defs").isObject()
                    ? mapper.convertValue(schema.get("$defs"), MAP_TYPE) : null;
            Map<String, Object> definitions = schema.has("definitions") && schema.get("definitions").isObject()
                    ? mapper.convertValue(schema.get("definitions"), MAP_TYPE) : null;
            return new McpSchema.JsonSchema(type, properties, required, additionalProperties, defs, definitions);
        } catch (Exception e) {
            return new McpSchema.JsonSchema("object", Map.of(), List.of(), null, null, null);
        }
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

    private JsonNode jsonRpcSuccess(JsonNode id, Object result) {
        McpSchema.JSONRPCResponse response = new McpSchema.JSONRPCResponse(
                JSON_RPC_VERSION, nodeToObject(id), result, null);
        return mapper.valueToTree(response);
    }

    private JsonNode jsonRpcError(JsonNode id, int code, String message) {
        McpSchema.JSONRPCResponse.JSONRPCError error =
                new McpSchema.JSONRPCResponse.JSONRPCError(code, message, null);
        McpSchema.JSONRPCResponse response = new McpSchema.JSONRPCResponse(
                JSON_RPC_VERSION, nodeToObject(id), null, error);
        return mapper.valueToTree(response);
    }

    private Object nodeToObject(JsonNode id) {
        if (id == null || id.isNull()) {
            return null;
        }
        return mapper.convertValue(id, Object.class);
    }

    private String extractRequestedProtocolVersion(JsonNode root, String headerProtocolVersion) {
        if (root.isObject() && root.has("method") && McpSchema.METHOD_INITIALIZE.equals(root.get("method").asText())
                && root.has("params") && root.get("params").has("protocolVersion")) {
            return root.get("params").get("protocolVersion").asText();
        }
        return headerProtocolVersion;
    }

    public String negotiateProtocolVersion(String requestedProtocolVersion) {
        if (Set.of(DEFAULT_PROTOCOL_VERSION, LEGACY_PROTOCOL_VERSION).contains(requestedProtocolVersion)) {
            return requestedProtocolVersion;
        }
        return DEFAULT_PROTOCOL_VERSION;
    }

    private String bearerToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }

    private String clientIp(HttpServletRequest request) {
        return request != null ? request.getRemoteAddr() : "";
    }

    private void publishCallRecord(CallRecordContext recordContext) {
        try {
            callRecorder.record(
                    recordContext.serverCode,
                    recordContext.toolName,
                    recordContext.clientIp,
                    recordContext.traceId,
                    recordContext.mcpMethod,
                    recordContext.success,
                    recordContext.statusCode,
                    recordContext.durationMs(),
                    recordContext.errorSummary);
        } catch (Exception e) {
            log.warn("Failed to publish gateway call record: server={} method={} traceId={}",
                    recordContext.serverCode, recordContext.mcpMethod, recordContext.traceId, e);
        }
    }

    public record ProtocolResult(JsonNode response, boolean initialized, String protocolVersion) {
        static ProtocolResult withResponse(JsonNode response, boolean initialized, String protocolVersion) {
            return new ProtocolResult(response, initialized, protocolVersion);
        }
    }

    private record SingleResult(JsonNode response, boolean initialized) {
        static SingleResult response(JsonNode response) {
            return new SingleResult(response, false);
        }

        static SingleResult initialized(JsonNode response) {
            return new SingleResult(response, true);
        }

        static SingleResult noResponse() {
            return new SingleResult(null, false);
        }
    }

    private static class CallRecordContext {
        private final String serverCode;
        private final String clientIp;
        private final String traceId;
        private final long startNanos = System.nanoTime();
        private String mcpMethod = "unknown";
        private String toolName;
        private boolean success;
        private int statusCode;
        private String errorSummary = "INTERNAL_ERROR";

        private CallRecordContext(String serverCode, String clientIp, String traceId) {
            this.serverCode = serverCode;
            this.clientIp = clientIp;
            this.traceId = traceId;
        }

        private void method(String mcpMethod) {
            if (mcpMethod != null && !mcpMethod.isBlank()) {
                this.mcpMethod = mcpMethod;
            }
        }

        private void tool(String toolName) {
            this.toolName = toolName;
        }

        private void succeed() {
            this.success = true;
            this.statusCode = 0;
            this.errorSummary = null;
        }

        private void fail(String errorSummary) {
            this.success = false;
            this.errorSummary = errorSummary;
        }

        private void result(boolean success, int statusCode, String errorSummary) {
            this.success = success;
            this.statusCode = statusCode;
            this.errorSummary = success ? null : (errorSummary != null ? errorSummary : "TOOL_EXECUTION_FAILED");
        }

        private int durationMs() {
            long duration = (System.nanoTime() - startNanos) / 1_000_000;
            return duration > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) duration;
        }
    }
}
