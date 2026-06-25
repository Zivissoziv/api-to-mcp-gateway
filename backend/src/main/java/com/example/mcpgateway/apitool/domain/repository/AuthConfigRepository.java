package com.example.mcpgateway.apitool.domain.repository;

import com.example.mcpgateway.apitool.domain.model.AuthConfig;

import java.util.Optional;

public interface AuthConfigRepository {
    Optional<AuthConfig> findById(long id);
    AuthConfig save(AuthConfig config);
    void deleteById(long id);
}
