package com.example.mcpgateway.gateway.domain.repository;

import com.example.mcpgateway.gateway.domain.model.McpServerAuth;

import java.util.Optional;

public interface McpServerAuthRepository {
    Optional<McpServerAuth> findByServerId(long serverId);
    McpServerAuth save(McpServerAuth auth);
    void update(McpServerAuth auth);
    void deleteByServerId(long serverId);
}
