package com.example.mcpgateway.apitool.application.service;

import com.example.mcpgateway.apitool.domain.model.*;
import com.example.mcpgateway.apitool.domain.repository.McpServerRepository;
import com.example.mcpgateway.apitool.domain.repository.McpServerToolRepository;
import com.example.mcpgateway.common.crypto.EncryptionService;
import com.example.mcpgateway.gateway.domain.model.McpServerAuth;
import com.example.mcpgateway.gateway.domain.repository.McpServerAuthRepository;
import com.example.mcpgateway.identity.infrastructure.security.BcryptPasswordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class McpServerService {
    private final McpServerRepository servers;
    private final McpServerToolRepository serverTools;
    private final McpServerAuthRepository authRepo;
    private final BcryptPasswordService passwords;
    private final EncryptionService encryption;

    public McpServerService(McpServerRepository servers, McpServerToolRepository serverTools,
                            McpServerAuthRepository authRepo, BcryptPasswordService passwords,
                            EncryptionService encryption) {
        this.servers = servers; this.serverTools = serverTools;
        this.authRepo = authRepo; this.passwords = passwords;
        this.encryption = encryption;
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
        authRepo.deleteByServerId(id);
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

    // -- Publish / Unpublish / MCP Key --

    @Transactional
    public PublishResult publish(long id, String mcpKey) {
        McpServer server = get(id);
        if (server.status() == ServerStatus.PUBLISHED)
            throw new AlreadyPublishedException(server.code());
        List<McpServerTool> tools = serverTools.findByServerId(id);
        if (tools.isEmpty())
            throw new PublishValidationException("Server " + server.code() + " has no tools bound");

        String rawKey = (mcpKey != null && !mcpKey.isBlank()) ? mcpKey : UUID.randomUUID().toString();
        String keyHash = passwords.encode(rawKey);
        String keyEnc = encryption.encrypt(rawKey);
        Instant now = Instant.now();

        // Upsert MCP Key
        var existingAuth = authRepo.findByServerId(id);
        if (existingAuth.isPresent()) {
            authRepo.update(new McpServerAuth(existingAuth.get().id(), id, keyHash, keyEnc,
                    existingAuth.get().createdAt(), now));
        } else {
            authRepo.save(new McpServerAuth(null, id, keyHash, keyEnc, now, now));
        }

        // Update server status
        McpServer published = new McpServer(id, server.code(), server.name(), server.description(),
                ServerStatus.PUBLISHED, server.createdBy(), server.createdAt(), now);
        servers.update(published);

        return new PublishResult(rawKey, published);
    }

    @Transactional
    public McpServer unpublish(long id) {
        McpServer server = get(id);
        if (server.status() != ServerStatus.PUBLISHED)
            throw new NotPublishedException(server.code());
        Instant now = Instant.now();
        McpServer draft = new McpServer(id, server.code(), server.name(), server.description(),
                ServerStatus.DRAFT, server.createdBy(), server.createdAt(), now);
        servers.update(draft);
        return draft;
    }

    @Transactional
    public String resetMcpKey(long id, String newKey) {
        get(id); // validate exists
        String rawKey = (newKey != null && !newKey.isBlank()) ? newKey : UUID.randomUUID().toString();
        String keyHash = passwords.encode(rawKey);
        String keyEnc = encryption.encrypt(rawKey);
        Instant now = Instant.now();
        var existing = authRepo.findByServerId(id)
                .orElseThrow(() -> new ServerNotConfiguredException(id));
        authRepo.update(new McpServerAuth(existing.id(), id, keyHash, keyEnc, existing.createdAt(), now));
        return rawKey;
    }

    public String getMcpKey(long id) {
        get(id); // validate exists
        return authRepo.findByServerId(id).map(a -> a.mcpKeyHash()).orElse(null);
    }

    /** Return the raw (plaintext) MCP key — decrypted from the stored encrypted copy.
     *  If no encrypted key exists yet (e.g. upgraded from an older version), a new key
     *  is generated, encrypted, and persisted automatically. */
    @Transactional
    public String getRawMcpKey(long id) {
        get(id); // validate exists
        var auth = authRepo.findByServerId(id).orElse(null);
        if (auth == null) return null;
        if (auth.mcpKeyEnc() != null) {
            return encryption.decrypt(auth.mcpKeyEnc());
        }
        // No encrypted key stored — generate a new one
        String rawKey = UUID.randomUUID().toString();
        String keyHash = passwords.encode(rawKey);
        String keyEnc = encryption.encrypt(rawKey);
        Instant now = Instant.now();
        authRepo.update(new McpServerAuth(auth.id(), id, keyHash, keyEnc, auth.createdAt(), now));
        return rawKey;
    }

    public record PublishResult(String rawMcpKey, McpServer server) {}

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
    public static class AlreadyPublishedException extends RuntimeException {
        public AlreadyPublishedException(String code) { super("Server already published: " + code); }
    }
    public static class NotPublishedException extends RuntimeException {
        public NotPublishedException(String code) { super("Server is not published: " + code); }
    }
    public static class PublishValidationException extends RuntimeException {
        public PublishValidationException(String msg) { super(msg); }
    }
    public static class ServerNotConfiguredException extends RuntimeException {
        public ServerNotConfiguredException(long id) { super("Server auth not configured: " + id); }
    }
}
