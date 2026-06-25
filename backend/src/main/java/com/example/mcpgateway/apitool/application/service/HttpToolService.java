package com.example.mcpgateway.apitool.application.service;

import com.example.mcpgateway.apitool.domain.model.*;
import com.example.mcpgateway.apitool.domain.repository.HttpToolRepository;
import com.example.mcpgateway.apitool.domain.repository.ParameterMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class HttpToolService {
    private final HttpToolRepository tools;
    private final ParameterMappingRepository mappings;
    private final SchemaConversionService schemaService;
    public HttpToolService(HttpToolRepository tools, ParameterMappingRepository mappings,
                           SchemaConversionService schemaService) {
        this.tools = tools; this.mappings = mappings; this.schemaService = schemaService;
    }

    public List<HttpTool> list() { return tools.findAll(); }

    public HttpTool get(long id) {
        return tools.findById(id).orElseThrow(() -> new ToolNotFoundException(id));
    }

    public List<ParameterMapping> getMappings(long toolId) {
        return mappings.findByToolId(toolId);
    }

    /** 从参数映射表聚合生成 Tool 的完整 Input JSON Schema */
    public String getInputSchema(long toolId) {
        List<ParameterMapping> ms = mappings.findByToolId(toolId);
        return schemaService.toJsonSchema(ms.stream().map(m -> new SchemaConversionService.SchemaField(
                m.name(), guessType(m.schemaJson()), m.description(), m.required())).toList());
    }

    /** 从 schemaJson 字段中尝试提取 type，默认 string */
    private String guessType(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return "string";
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(schemaJson);
            return node.has("type") ? node.get("type").asText() : "string";
        } catch (Exception e) { return "string"; }
    }

    @Transactional
    public HttpTool create(String name, String description, HttpMethod httpMethod,
                           String urlTemplate, String headers,
                           String createdBy,
                           List<ParameterMappingRequest> parameterMappings) {
        if (tools.existsByName(name)) throw new DuplicateToolNameException(name);
        Instant now = Instant.now();
        HttpTool tool = tools.save(new HttpTool(
                null, name, description, httpMethod, urlTemplate, headers,
                null, ToolStatus.DRAFT, createdBy, now, now));
        saveMappings(tool.id(), parameterMappings, now);
        return tool;
    }

    @Transactional
    public HttpTool update(long id, String name, String description, HttpMethod httpMethod,
                           String urlTemplate, String headers,
                           List<ParameterMappingRequest> parameterMappings) {
        HttpTool existing = get(id);
        if (!existing.name().equals(name) && tools.existsByName(name))
            throw new DuplicateToolNameException(name);
        Instant now = Instant.now();
        HttpTool updated = new HttpTool(id, name, description, httpMethod, urlTemplate,
                headers, existing.authConfigId(),
                existing.status(), existing.createdBy(), existing.createdAt(), now);
        tools.update(updated);
        mappings.deleteByToolId(id);
        saveMappings(id, parameterMappings, now);
        return updated;
    }

    @Transactional
    public void delete(long id) {
        tools.findById(id).orElseThrow(() -> new ToolNotFoundException(id));
        mappings.deleteByToolId(id);
        tools.deleteById(id);
    }

    private void saveMappings(Long toolId, List<ParameterMappingRequest> reqs, Instant now) {
        if (reqs == null) return;
        for (int i = 0; i < reqs.size(); i++) {
            ParameterMappingRequest req = reqs.get(i);
            mappings.save(new ParameterMapping(null, toolId, req.name(), req.paramSource(),
                    req.paramLocation(), req.schemaJson(), req.required(), req.description(),
                    i, now));
        }
    }

    public record ParameterMappingRequest(
            String name, ParamSource paramSource, String paramLocation,
            String schemaJson, boolean required, String description) {}

    public static class ToolNotFoundException extends RuntimeException {
        public ToolNotFoundException(long id) { super("Tool not found: " + id); }
    }
    public static class DuplicateToolNameException extends RuntimeException {
        public DuplicateToolNameException(String name) { super("Tool name already exists: " + name); }
    }
}
