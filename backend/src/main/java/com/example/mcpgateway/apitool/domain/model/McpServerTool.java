package com.example.mcpgateway.apitool.domain.model;

import java.time.Instant;

public record McpServerTool(
        Long id,
        Long serverId,
        Long toolId,
        int sortOrder,
        Instant createdAt
) {}
