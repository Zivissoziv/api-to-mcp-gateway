package com.example.mcpgateway.apitool.domain.model;

import java.time.Instant;

public record NetworkAllowlist(
        Long id,
        String pattern,
        PatternType patternType,
        String description,
        boolean enabled,
        String createdBy,
        Instant createdAt
) {}
