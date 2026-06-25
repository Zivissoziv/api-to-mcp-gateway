package com.example.mcpgateway.identity.domain.repository;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository {
    void save(long id, String userId, String tokenHash, Instant expiresAt);
    Optional<String> findActiveUserId(String tokenHash, Instant now);
    void revoke(String tokenHash);
    void revokeAllForUser(String userId);
}
