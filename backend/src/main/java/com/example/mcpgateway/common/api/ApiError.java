package com.example.mcpgateway.common.api;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String traceId,
        Instant timestamp
) {
}

