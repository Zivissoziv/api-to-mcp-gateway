package com.example.mcpgateway.aitest.domain.model;

import java.time.Instant;

public record AiModelConfig(
        Long id,
        String name,
        String baseUrl,
        String apiKeyEnc,
        String model,
        int timeoutSeconds,
        boolean enabled,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
