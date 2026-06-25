package com.example.mcpgateway.identity.domain.model;

import java.time.Instant;

public record User(
        String id,
        String username,
        String passwordHash,
        UserRole role,
        UserStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }
}
