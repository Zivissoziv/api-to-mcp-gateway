package com.example.mcpgateway.gateway.application.service;

import com.example.mcpgateway.apitool.domain.model.HttpTool;
import com.example.mcpgateway.apitool.domain.model.McpServer;
import com.example.mcpgateway.apitool.domain.model.McpServerTool;
import com.example.mcpgateway.apitool.domain.model.ParameterMapping;
import com.example.mcpgateway.apitool.domain.model.ServerStatus;
import com.example.mcpgateway.apitool.domain.repository.HttpToolRepository;
import com.example.mcpgateway.apitool.domain.repository.McpServerRepository;
import com.example.mcpgateway.apitool.domain.repository.McpServerToolRepository;
import com.example.mcpgateway.apitool.domain.repository.ParameterMappingRepository;
import com.example.mcpgateway.apitool.application.service.SchemaConversionService;
import com.example.mcpgateway.apitool.application.service.SchemaConversionService.SchemaField;
import com.example.mcpgateway.gateway.domain.model.PublishedServer;
import com.example.mcpgateway.gateway.domain.model.PublishedTool;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class McpServerProviderImpl implements McpServerProvider {
    private final McpServerRepository servers;
    private final McpServerToolRepository serverTools;
    private final HttpToolRepository tools;
    private final ParameterMappingRepository mappings;
    private final SchemaConversionService schemas;

    public McpServerProviderImpl(McpServerRepository servers, McpServerToolRepository serverTools,
                                  HttpToolRepository tools, ParameterMappingRepository mappings,
                                  SchemaConversionService schemas) {
        this.servers = servers; this.serverTools = serverTools;
        this.tools = tools; this.mappings = mappings; this.schemas = schemas;
    }

    @Override
    public Optional<PublishedServer> load(String serverCode) {
        Optional<McpServer> serverOpt = servers.findByCode(serverCode);
        if (serverOpt.isEmpty() || serverOpt.get().status() != ServerStatus.PUBLISHED)
            return Optional.empty();

        McpServer server = serverOpt.get();
        return Optional.of(toPublishedServer(server));
    }

    @Override
    public Optional<PublishedServer> loadById(long serverId) {
        Optional<McpServer> serverOpt = servers.findById(serverId);
        if (serverOpt.isEmpty() || serverOpt.get().status() != ServerStatus.PUBLISHED)
            return Optional.empty();

        return Optional.of(toPublishedServer(serverOpt.get()));
    }

    private PublishedServer toPublishedServer(McpServer server) {
        List<McpServerTool> bindings = serverTools.findByServerId(server.id());
        List<PublishedTool> publishedTools = bindings.stream()
                .map(b -> tools.findById(b.toolId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::toPublishedTool)
                .toList();

        return new PublishedServer(
                server.id(), server.code(), server.name(),
                server.description(), publishedTools);
    }

    private PublishedTool toPublishedTool(HttpTool tool) {
        List<ParameterMapping> params = mappings.findByToolId(tool.id());
        List<SchemaField> schemaFields = params.stream()
                .map(p -> new SchemaField(p.name(), resolveType(p.schemaJson()),
                        p.description(), p.required()))
                .toList();
        String schema;
        try {
            schema = schemas.toJsonSchema(schemaFields);
        } catch (Exception e) {
            schema = "{\"type\":\"object\",\"properties\":{}}";
        }

        return new PublishedTool(
                tool.id(), tool.name(), tool.description(), schema,
                params.stream().map(p -> new PublishedTool.ParamDef(
                        p.name(), p.paramSource().name(),
                        p.paramLocation(), resolveType(p.schemaJson()),
                        p.required(), p.description()
                )).toList()
        );
    }

    private String resolveType(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return "string";
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(schemaJson);
            return node.has("type") ? node.get("type").asText() : "string";
        } catch (Exception e) {
            return "string";
        }
    }
}
