package com.example.mcpgateway.apitool.domain.model;

import java.time.Instant;

public record AuthConfig(
        Long id,
        AuthType authType,
        String config,
        Instant createdAt,
        Instant updatedAt
) {}
