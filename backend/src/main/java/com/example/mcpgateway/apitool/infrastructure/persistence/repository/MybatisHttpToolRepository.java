package com.example.mcpgateway.apitool.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.mcpgateway.apitool.domain.model.HttpTool;
import com.example.mcpgateway.apitool.domain.model.HttpMethod;
import com.example.mcpgateway.apitool.domain.model.ToolStatus;
import com.example.mcpgateway.apitool.domain.repository.HttpToolRepository;
import com.example.mcpgateway.apitool.infrastructure.persistence.mapper.HttpToolMapper;
import com.example.mcpgateway.apitool.infrastructure.persistence.row.HttpToolRow;
import org.springframework.stereotype.Repository;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

@Repository
public class MybatisHttpToolRepository implements HttpToolRepository {
    private final HttpToolMapper mapper;
    public MybatisHttpToolRepository(HttpToolMapper mapper) { this.mapper = mapper; }

    @Override public Optional<HttpTool> findById(long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::domain);
    }
    @Override public List<HttpTool> findAll() {
        return mapper.selectList(null).stream().map(this::domain).toList();
    }
    @Override public HttpTool save(HttpTool tool) {
        HttpToolRow row = row(tool);
        mapper.insert(row);
        HttpToolRow saved = mapper.selectById(row.id);
        return domain(saved);
    }
    @Override public void update(HttpTool tool) {
        mapper.updateById(row(tool));
    }
    @Override public void deleteById(long id) {
        mapper.deleteById(id);
    }
    @Override public boolean existsByName(String name) {
        return mapper.selectCount(new LambdaQueryWrapper<HttpToolRow>()
                .eq(HttpToolRow::getName, name)) > 0;
    }

    private HttpTool domain(HttpToolRow r) {
        return new HttpTool(r.id, r.name, r.description, HttpMethod.valueOf(r.httpMethod),
                r.urlTemplate, r.headers, r.authConfigId,
                ToolStatus.valueOf(r.status), r.createdBy,
                r.createdAt.toInstant(ZoneOffset.UTC), r.updatedAt.toInstant(ZoneOffset.UTC));
    }
    private HttpToolRow row(HttpTool t) {
        HttpToolRow r = new HttpToolRow();
        r.id = t.id(); r.name = t.name(); r.description = t.description();
        r.httpMethod = t.httpMethod().name(); r.urlTemplate = t.urlTemplate();
        r.headers = t.headers(); r.authConfigId = t.authConfigId();
        r.status = t.status().name(); r.createdBy = t.createdBy();
        if (t.createdAt() != null) r.createdAt = t.createdAt().atZone(ZoneOffset.UTC).toLocalDateTime();
        if (t.updatedAt() != null) r.updatedAt = t.updatedAt().atZone(ZoneOffset.UTC).toLocalDateTime();
        return r;
    }
}
