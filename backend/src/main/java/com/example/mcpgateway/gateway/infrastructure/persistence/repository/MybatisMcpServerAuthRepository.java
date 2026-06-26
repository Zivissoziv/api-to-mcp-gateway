package com.example.mcpgateway.gateway.infrastructure.persistence.repository;

import com.example.mcpgateway.gateway.domain.model.McpServerAuth;
import com.example.mcpgateway.gateway.domain.repository.McpServerAuthRepository;
import com.example.mcpgateway.gateway.infrastructure.persistence.mapper.McpServerAuthMapper;
import com.example.mcpgateway.gateway.infrastructure.persistence.row.McpServerAuthRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class MybatisMcpServerAuthRepository implements McpServerAuthRepository {
    private final McpServerAuthMapper mapper;
    public MybatisMcpServerAuthRepository(McpServerAuthMapper mapper) { this.mapper = mapper; }

    @Override public Optional<McpServerAuth> findByServerId(long serverId) {
        var rows = mapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<McpServerAuthRow>()
                        .eq(McpServerAuthRow::getServerId, serverId));
        return rows.isEmpty() ? Optional.empty() : Optional.of(domain(rows.get(0)));
    }

    @Override public McpServerAuth save(McpServerAuth auth) {
        McpServerAuthRow row = row(auth);
        mapper.insert(row);
        McpServerAuthRow saved = mapper.selectById(row.id);
        return domain(saved);
    }

    @Override public void update(McpServerAuth auth) {
        mapper.updateById(row(auth));
    }

    @Override public void deleteByServerId(long serverId) {
        mapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<McpServerAuthRow>()
                .eq(McpServerAuthRow::getServerId, serverId));
    }

    private McpServerAuth domain(McpServerAuthRow r) {
        return new McpServerAuth(r.id, r.serverId, r.mcpKeyHash, r.mcpKeyEnc,
                r.createdAt.toInstant(ZoneOffset.UTC), r.updatedAt.toInstant(ZoneOffset.UTC));
    }

    private McpServerAuthRow row(McpServerAuth e) {
        McpServerAuthRow r = new McpServerAuthRow();
        if (e.id() != null) r.id = e.id();
        r.serverId = e.serverId();
        r.mcpKeyHash = e.mcpKeyHash();
        r.mcpKeyEnc = e.mcpKeyEnc();
        return r;
    }
}
