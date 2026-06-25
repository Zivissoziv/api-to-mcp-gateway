package com.example.mcpgateway.apitool.domain.repository;

import com.example.mcpgateway.apitool.domain.model.McpServer;

import java.util.List;
import java.util.Optional;

public interface McpServerRepository {
    Optional<McpServer> findById(long id);
    Optional<McpServer> findByCode(String code);
    List<McpServer> findAll();
    McpServer save(McpServer server);
    void update(McpServer server);
    void deleteById(long id);
}
