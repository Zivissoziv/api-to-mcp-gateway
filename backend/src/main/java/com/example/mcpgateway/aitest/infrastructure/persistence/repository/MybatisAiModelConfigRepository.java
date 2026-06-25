package com.example.mcpgateway.aitest.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mcpgateway.aitest.domain.model.AiModelConfig;
import com.example.mcpgateway.aitest.domain.repository.AiModelConfigRepository;
import com.example.mcpgateway.aitest.infrastructure.persistence.mapper.AiModelConfigMapper;
import com.example.mcpgateway.aitest.infrastructure.persistence.row.AiModelConfigRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisAiModelConfigRepository implements AiModelConfigRepository {
    private final AiModelConfigMapper mapper;
    public MybatisAiModelConfigRepository(AiModelConfigMapper mapper) { this.mapper = mapper; }

    @Override public List<AiModelConfig> findAll() {
        return mapper.selectList(null).stream().map(this::domain).toList();
    }

    @Override public Optional<AiModelConfig> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::domain);
    }

    @Override public Optional<AiModelConfig> findEnabled() {
        var wrapper = new LambdaQueryWrapper<AiModelConfigRow>()
                .eq(AiModelConfigRow::getEnabled, 1);
        var rows = mapper.selectList(wrapper);
        return rows.isEmpty() ? Optional.empty() : Optional.of(domain(rows.get(0)));
    }

    @Override public AiModelConfig save(AiModelConfig config) {
        AiModelConfigRow row = row(config);
        mapper.insert(row);
        AiModelConfigRow saved = mapper.selectById(row.id);
        return domain(saved);
    }

    @Override public void update(AiModelConfig config) {
        mapper.updateById(row(config));
    }

    @Override public void deleteById(long id) {
        mapper.deleteById(id);
    }

    private AiModelConfig domain(AiModelConfigRow r) {
        return new AiModelConfig(r.id, r.name, r.baseUrl, r.apiKeyEnc, r.model,
                r.timeoutSeconds, r.enabled == 1, r.createdBy,
                r.createdAt.toInstant(ZoneOffset.UTC), r.updatedAt.toInstant(ZoneOffset.UTC));
    }

    private AiModelConfigRow row(AiModelConfig c) {
        AiModelConfigRow r = new AiModelConfigRow();
        if (c.id() != null) r.id = c.id();
        r.name = c.name();
        r.baseUrl = c.baseUrl();
        r.apiKeyEnc = c.apiKeyEnc();
        r.model = c.model();
        r.timeoutSeconds = c.timeoutSeconds();
        r.enabled = c.enabled() ? 1 : 0;
        r.createdBy = c.createdBy();
        return r;
    }
}
