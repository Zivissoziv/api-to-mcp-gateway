package com.example.mcpgateway.aitest.application.service;

import com.example.mcpgateway.aitest.domain.model.AiModelConfig;
import com.example.mcpgateway.aitest.domain.repository.AiModelConfigRepository;
import com.example.mcpgateway.common.crypto.EncryptionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class AiModelConfigService {
    private final AiModelConfigRepository repository;
    private final EncryptionService encryption;

    public AiModelConfigService(AiModelConfigRepository repository, EncryptionService encryption) {
        this.repository = repository;
        this.encryption = encryption;
    }

    public List<AiModelConfig> list() {
        return repository.findAll().stream()
                .map(c -> new AiModelConfig(c.id(), c.name(), c.baseUrl(), null,
                        c.model(), c.timeoutSeconds(), c.enabled(),
                        c.createdBy(), c.createdAt(), c.updatedAt()))
                .toList();
    }

    public AiModelConfig get(long id) {
        return repository.findById(id)
                .map(c -> new AiModelConfig(c.id(), c.name(), c.baseUrl(), null,
                        c.model(), c.timeoutSeconds(), c.enabled(),
                        c.createdBy(), c.createdAt(), c.updatedAt()))
                .orElseThrow(() -> new ConfigNotFoundException(id));
    }

    @Transactional
    public AiModelConfig create(String name, String baseUrl, String apiKey,
                                 String model, int timeoutSeconds, boolean enabled,
                                 String createdBy) {
        // Deactivate other configs if this one is enabled
        if (enabled) {
            deactivateAll();
        }
        String encrypted = encryption.encrypt(apiKey);
        Instant now = Instant.now();
        return repository.save(new AiModelConfig(
                null, name, baseUrl, encrypted, model, timeoutSeconds, enabled,
                createdBy, now, now));
    }

    @Transactional
    public AiModelConfig update(long id, String name, String baseUrl, String apiKey,
                                 String model, Integer timeoutSeconds, Boolean enabled) {
        AiModelConfig existing = repository.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException(id));
        if (enabled != null && enabled) {
            deactivateAll();
        }
        String encKey = (apiKey != null && !apiKey.isBlank())
                ? encryption.encrypt(apiKey) : existing.apiKeyEnc();
        String finalName = name != null ? name : existing.name();
        String finalBaseUrl = baseUrl != null ? baseUrl : existing.baseUrl();
        String finalModel = model != null ? model : existing.model();
        int finalTimeout = timeoutSeconds != null ? timeoutSeconds : existing.timeoutSeconds();
        boolean finalEnabled = enabled != null ? enabled : existing.enabled();
        Instant now = Instant.now();
        AiModelConfig updated = new AiModelConfig(id, finalName, finalBaseUrl, encKey, finalModel,
                finalTimeout, finalEnabled, existing.createdBy(), existing.createdAt(), now);
        repository.update(updated);
        // Return without API key
        return new AiModelConfig(id, finalName, finalBaseUrl, null, finalModel,
                finalTimeout, finalEnabled, existing.createdBy(), existing.createdAt(), now);
    }

    @Transactional
    public void delete(long id) {
        repository.deleteById(id);
    }

    private void deactivateAll() {
        var all = repository.findAll();
        for (var c : all) {
            if (c.enabled()) {
                Instant now = Instant.now();
                repository.update(new AiModelConfig(c.id(), c.name(), c.baseUrl(),
                        c.apiKeyEnc(), c.model(), c.timeoutSeconds(), false,
                        c.createdBy(), c.createdAt(), now));
            }
        }
    }

    public static class ConfigNotFoundException extends RuntimeException {
        public ConfigNotFoundException(long id) { super("AI model config not found: " + id); }
    }
}
