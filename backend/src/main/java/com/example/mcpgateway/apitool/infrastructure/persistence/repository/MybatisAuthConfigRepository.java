package com.example.mcpgateway.apitool.infrastructure.persistence.repository;

import com.example.mcpgateway.apitool.domain.model.AuthConfig;
import com.example.mcpgateway.apitool.domain.model.AuthType;
import com.example.mcpgateway.apitool.domain.repository.AuthConfigRepository;
import com.example.mcpgateway.apitool.infrastructure.persistence.mapper.AuthConfigMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.AuthConfigRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class MybatisAuthConfigRepository implements AuthConfigRepository {
    private final AuthConfigMapper mapper;
    public MybatisAuthConfigRepository(AuthConfigMapper mapper) { this.mapper = mapper; }

    @Override public Optional<AuthConfig> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::domain);
    }
    @Override public AuthConfig save(AuthConfig config) {
        AuthConfigRow row = row(config);
        if (config.id() != null) { row.id = config.id(); mapper.updateById(row); }
        else mapper.insert(row);
        return domain(mapper.selectById(row.id));
    }
    @Override public void deleteById(long id) { mapper.deleteById(id); }

    private AuthConfig domain(AuthConfigRow r) {
        return new AuthConfig(r.id, AuthType.valueOf(r.authType), r.config,
                r.createdAt.toInstant(ZoneOffset.UTC), r.updatedAt.toInstant(ZoneOffset.UTC));
    }
    private AuthConfigRow row(AuthConfig c) {
        AuthConfigRow r = new AuthConfigRow();
        r.authType = c.authType().name();
        r.config = c.config();
        return r;
    }
}
