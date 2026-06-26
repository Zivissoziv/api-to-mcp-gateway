package com.example.mcpgateway.gateway.domain.model;

import java.time.Instant;

public record ServerCallStats(
        String serverCode,
        long callCount,
        long successCount,
        long uniqueIps,
        double avgDurationMs,
        Instant lastCallAt
) {}
