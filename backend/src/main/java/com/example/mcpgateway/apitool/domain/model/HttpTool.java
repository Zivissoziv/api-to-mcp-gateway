package com.example.mcpgateway.apitool.domain.model;

import java.time.Instant;

public record HttpTool(
        Long id,
        String name,
        String description,
        HttpMethod httpMethod,
        String urlTemplate,
        String headers,
        Long authConfigId,
        ToolStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}

