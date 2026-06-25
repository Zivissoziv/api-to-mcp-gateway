package com.example.mcpgateway.apitool.domain.model;

import java.time.Instant;

public record ParameterMapping(
        Long id,
        Long toolId,
        String name,
        ParamSource paramSource,
        String paramLocation,
        String schemaJson,
        boolean required,
        String description,
        int sortOrder,
        Instant createdAt
) {}
