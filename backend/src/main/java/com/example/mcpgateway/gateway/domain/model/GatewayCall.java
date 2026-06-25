package com.example.mcpgateway.gateway.domain.model;

import java.time.Instant;

public record GatewayCall(
        Long id,
        String serverCode,
        String toolName,
        String clientIp,
        String traceId,
        String mcpMethod,
        boolean success,
        int statusCode,
        int durationMs,
        String errorSummary,
        Instant createdAt
) {}
