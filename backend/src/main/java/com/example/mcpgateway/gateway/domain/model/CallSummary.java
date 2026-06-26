package com.example.mcpgateway.gateway.domain.model;

public record CallSummary(
        long totalCalls,
        long uniqueServers,
        long uniqueTools,
        long uniqueIps
) {}
