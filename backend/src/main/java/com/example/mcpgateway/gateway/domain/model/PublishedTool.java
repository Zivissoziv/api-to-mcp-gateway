package com.example.mcpgateway.gateway.domain.model;

import java.util.List;

public record PublishedTool(
        long id,
        String name,
        String description,
        String inputSchema,
        List<ParamDef> parameters
) {
    public record ParamDef(
            String name,
            String paramSource,
            String paramLocation,
            String type,
            boolean required,
            String description
    ) {}
}
