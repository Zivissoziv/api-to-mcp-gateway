package com.example.mcpgateway.gateway.domain.model;

import java.time.Instant;

public record ToolCallStats(
        String serverCode,
        String toolName,
        long callCount,
        long successCount,
        long uniqueIps,
        double avgDurationMs,
        Instant lastCallAt
) {}
