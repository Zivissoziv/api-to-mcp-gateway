package com.example.mcpgateway.apitool.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SchemaConversionService {

    private final ObjectMapper mapper = new ObjectMapper();

    // ── 从 JSON Schema 解析为可视化字段 ──

    public List<SchemaField> fromJsonSchema(String schemaJson) {
        try {
            JsonNode root = mapper.readTree(schemaJson);
            if (!root.has("properties")) return List.of();
            List<String> required = new ArrayList<>();
            if (root.has("required") && root.get("required").isArray()) {
                root.get("required").forEach(n -> required.add(n.asText()));
            }
            List<SchemaField> fields = new ArrayList<>();
            root.get("properties").fields().forEachRemaining(entry -> {
                String name = entry.getKey();
                JsonNode def = entry.getValue();
                fields.add(new SchemaField(
                        name,
                        def.has("type") ? def.get("type").asText() : "string",
                        def.has("description") ? def.get("description").asText() : "",
                        required.contains(name)
                ));
            });
            return fields;
        } catch (JsonProcessingException e) {
            throw new InvalidSchemaException(e.getMessage());
        }
    }

    // ── 从可视化字段生成 JSON Schema ──

    public String toJsonSchema(List<SchemaField> fields) {
        ObjectNode root = mapper.createObjectNode();
        root.put("type", "object");
        ObjectNode props = root.putObject("properties");
        ArrayNode required = root.putArray("required");
        for (SchemaField f : fields) {
            ObjectNode def = props.putObject(f.name());
            def.put("type", f.type());
            def.put("description", f.description());
            if (f.required()) required.add(f.name());
        }
        return root.toPrettyString();
    }

    // ── JSON Schema 校验 ──

    public void validate(String schemaJson) {
        try {
            JsonNode root = mapper.readTree(schemaJson);
            if (!root.isObject()) throw new InvalidSchemaException("Schema must be a JSON object");
            if (!root.has("type") || !"object".equals(root.get("type").asText())) {
                throw new InvalidSchemaException("Root schema type must be 'object'");
            }
        } catch (JsonProcessingException e) {
            throw new InvalidSchemaException("Invalid JSON: " + e.getMessage());
        }
    }

    public record SchemaField(String name, String type, String description, boolean required) {}

    public static class InvalidSchemaException extends RuntimeException {
        public InvalidSchemaException(String message) { super(message); }
    }
}
