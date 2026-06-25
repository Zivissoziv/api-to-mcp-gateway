package com.example.mcpgateway.apitool.domain.repository;

import com.example.mcpgateway.apitool.domain.model.McpServerTool;

import java.util.List;

public interface McpServerToolRepository {
    List<McpServerTool> findByServerId(long serverId);
    List<McpServerTool> findByToolId(long toolId);
    void save(McpServerTool binding);
    void deleteByServerIdAndToolId(long serverId, long toolId);
    void deleteByServerId(long serverId);
    boolean existsByServerIdAndToolId(long serverId, long toolId);
}
