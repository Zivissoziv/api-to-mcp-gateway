package com.example.mcpgateway.gateway.application.service;

import com.example.mcpgateway.gateway.domain.model.PublishedServer;

import java.util.Optional;

public interface McpServerProvider {
    Optional<PublishedServer> load(String serverCode);
    Optional<PublishedServer> loadById(long serverId);
}
