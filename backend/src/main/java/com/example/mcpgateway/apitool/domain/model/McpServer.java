package com.example.mcpgateway.apitool.domain.model;

import java.time.Instant;

public record McpServer(
        Long id,
        String code,
        String name,
        String description,
        ServerStatus status,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
