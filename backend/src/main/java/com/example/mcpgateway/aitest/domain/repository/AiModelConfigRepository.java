package com.example.mcpgateway.aitest.domain.repository;

import com.example.mcpgateway.aitest.domain.model.AiModelConfig;

import java.util.List;
import java.util.Optional;

public interface AiModelConfigRepository {
    List<AiModelConfig> findAll();
    Optional<AiModelConfig> findById(long id);
    Optional<AiModelConfig> findEnabled();
    AiModelConfig save(AiModelConfig config);
    void update(AiModelConfig config);
    void deleteById(long id);
}
