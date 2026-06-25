package com.example.mcpgateway.executor;

import java.util.List;

public record HttpToolDefinition(
        String httpMethod,
        String urlTemplate,
        String headers,
        List<ParameterMapping> parameterMappings
) {
    public record ParameterMapping(
            String name,
            String paramSource,
            String paramLocation,
            String type,
            boolean required,
            String description
    ) {}
}
