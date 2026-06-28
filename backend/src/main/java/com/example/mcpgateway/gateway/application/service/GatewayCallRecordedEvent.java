package com.example.mcpgateway.gateway.application.service;

import java.time.Instant;

public record GatewayCallRecordedEvent(
        String serverCode,
        String toolName,
        String clientIp,
        String traceId,
        String mcpMethod,
        boolean success,
        int statusCode,
        int durationMs,
        String errorSummary,
        Instant recordedAt
) {}
