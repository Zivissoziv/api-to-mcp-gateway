package com.example.mcpgateway.gateway.domain.model;

import java.time.Instant;

public record McpServerAuth(
        Long id,
        Long serverId,
        String mcpKeyHash,
        Instant createdAt,
        Instant updatedAt
) {}
