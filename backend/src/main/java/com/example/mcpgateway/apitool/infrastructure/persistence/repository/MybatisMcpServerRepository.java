package com.example.mcpgateway.apitool.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mcpgateway.apitool.domain.model.McpServer;
import com.example.mcpgateway.apitool.domain.model.ServerStatus;
import com.example.mcpgateway.apitool.domain.repository.McpServerRepository;
import com.example.mcpgateway.apitool.infrastructure.persistence.mapper.McpServerMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.McpServerRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisMcpServerRepository implements McpServerRepository {
    private final McpServerMapper mapper;
    public MybatisMcpServerRepository(McpServerMapper mapper) { this.mapper = mapper; }

    @Override public Optional<McpServer> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::domain);
    }
    @Override public Optional<McpServer> findByCode(String code) {
        return Optional.ofNullable(mapper.selectOne(
                new LambdaQueryWrapper<McpServerRow>().eq(McpServerRow::getCode, code)))
                .map(this::domain);
    }
    @Override public List<McpServer> findAll() {
        return mapper.selectList(null).stream().map(this::domain).toList();
    }
    @Override public McpServer save(McpServer server) {
        McpServerRow row = row(server);
        mapper.insert(row);
        McpServerRow saved = mapper.selectById(row.id);
        return domain(saved);
    }
    @Override public void update(McpServer server) {
        mapper.updateById(row(server));
    }
    @Override public void deleteById(long id) {
        mapper.deleteById(id);
    }

    private McpServer domain(McpServerRow r) {
        return new McpServer(r.id, r.code, r.name, r.description,
                ServerStatus.valueOf(r.status), r.createdBy,
                r.createdAt.toInstant(ZoneOffset.UTC), r.updatedAt.toInstant(ZoneOffset.UTC));
    }
    private McpServerRow row(McpServer s) {
        McpServerRow r = new McpServerRow();
        r.id = s.id(); r.code = s.code(); r.name = s.name(); r.description = s.description();
        r.status = s.status().name(); r.createdBy = s.createdBy();
        if (s.createdAt() != null) r.createdAt = s.createdAt().atZone(ZoneOffset.UTC).toLocalDateTime();
        if (s.updatedAt() != null) r.updatedAt = s.updatedAt().atZone(ZoneOffset.UTC).toLocalDateTime();
        return r;
    }
}
