package com.example.mcpgateway.apitool.application.service;

import com.example.mcpgateway.apitool.domain.model.*;
import com.example.mcpgateway.apitool.domain.repository.McpServerRepository;
import com.example.mcpgateway.apitool.domain.repository.McpServerToolRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class McpServerService {
    private final McpServerRepository servers;
    private final McpServerToolRepository serverTools;
    public McpServerService(McpServerRepository servers, McpServerToolRepository serverTools) {
        this.servers = servers; this.serverTools = serverTools;
    }

    public List<McpServer> list() { return servers.findAll(); }

    public McpServer get(long id) {
        return servers.findById(id).orElseThrow(() -> new ServerNotFoundException(id));
    }

    @Transactional
    public McpServer create(String code, String name, String description, String createdBy) {
        if (servers.findByCode(code).isPresent())
            throw new DuplicateServerCodeException(code);
        Instant now = Instant.now();
        return servers.save(new McpServer(
                null,
                code, name, description, ServerStatus.DRAFT, createdBy, now, now));
    }

    @Transactional
    public McpServer update(long id, String code, String name, String description) {
        McpServer existing = get(id);
        if (!existing.code().equals(code) && servers.findByCode(code).isPresent())
            throw new DuplicateServerCodeException(code);
        Instant now = Instant.now();
        McpServer updated = new McpServer(id, code, name, description,
                existing.status(), existing.createdBy(), existing.createdAt(), now);
        servers.update(updated);
        return updated;
    }

    @Transactional
    public void delete(long id) {
        servers.findById(id).orElseThrow(() -> new ServerNotFoundException(id));
        serverTools.deleteByServerId(id);
        servers.deleteById(id);
    }

    public List<McpServerTool> getTools(long serverId) {
        return serverTools.findByServerId(serverId);
    }

    @Transactional
    public McpServerTool bindTool(long serverId, long toolId) {
        servers.findById(serverId).orElseThrow(() -> new ServerNotFoundException(serverId));
        if (serverTools.existsByServerIdAndToolId(serverId, toolId))
            throw new ToolAlreadyBoundException(serverId, toolId);
        McpServerTool binding = new McpServerTool(null, serverId, toolId, 0, Instant.now());
        serverTools.save(binding);
        return binding;
    }

    @Transactional
    public void unbindTool(long serverId, long toolId) {
        serverTools.deleteByServerIdAndToolId(serverId, toolId);
    }

    public static class ServerNotFoundException extends RuntimeException {
        public ServerNotFoundException(long id) { super("Server not found: " + id); }
    }
    public static class DuplicateServerCodeException extends RuntimeException {
        public DuplicateServerCodeException(String code) { super("Server code already exists: " + code); }
    }
    public static class ToolAlreadyBoundException extends RuntimeException {
        public ToolAlreadyBoundException(long serverId, long toolId) {
            super("Tool " + toolId + " already bound to server " + serverId);
        }
    }
}
